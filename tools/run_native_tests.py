#!/usr/bin/env python3
"""Fresh instrumentation process per test; bounded evidence; no retries or skipped tests.
Runs exclusively against the disposable CI emulator and test-only debug APK.
"""
from __future__ import annotations
import json
from pathlib import Path
import re
import subprocess
import sys
import time

PACKAGE = 'com.local.focusfence'
ADMIN = PACKAGE + '/com.local.focusfence.security.BernardDeviceAdminReceiver'
RUNNER = PACKAGE + '.test/com.local.focusfence.qa.BernardTestRunner'
OUT = Path('qa-screens')
OUT.mkdir(exist_ok=True)

def adb(*args: str, timeout: int = 25, check: bool = True) -> str:
    result = subprocess.run(['adb', *args], capture_output=True, text=True, timeout=timeout)
    if check and result.returncode:
        raise RuntimeError(f'adb {args} failed: {result.stdout} {result.stderr}')
    return result.stdout + result.stderr

def evidence(label: str) -> None:
    probes = {
        'threads': ('shell', 'run-as', PACKAGE, 'cat', 'files/qa-thread-dump.txt'),
        'exits': ('shell', 'dumpsys', 'activity', 'exit-info', PACKAGE),
        'activities': ('shell', 'dumpsys', 'activity', 'activities'),
        'processes': ('shell', 'ps', '-A', '-T'),
        'logcat': ('logcat', '-d', '-t', '3000'),
    }
    for kind, args in probes.items():
        try:
            text = adb(*args, check=False, timeout=15)
        except Exception as error:
            text = str(error)
        (OUT / f'{label}-{kind}.txt').write_text(text)

def clean() -> None:
    adb('shell', 'settings', 'put', 'secure', 'enabled_accessibility_services', 'null')
    adb('shell', 'am', 'force-stop', PACKAGE)
    # A test may activate the admin. This only works because DEBUG is marked testOnly.
    adb('shell', 'dpm', 'remove-active-admin', ADMIN, check=False)
    result = adb('shell', 'pm', 'clear', PACKAGE)
    if 'Success' not in result:
        raise RuntimeError('Failed to reset the disposable test application: ' + result)
    adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
    adb('shell', 'settings', 'put', 'global', 'auto_time', '1')
    adb('shell', 'settings', 'put', 'global', 'auto_time_zone', '1')

def run_case(case: str, index: int, owner: bool) -> dict:
    label = f'{index:03d}-' + case.replace('#', '-').split('.')[-1]
    start = time.monotonic()
    raw = ''
    ok = False
    error = ''
    try:
        clean()
        if owner:
            result = adb('shell', 'dpm', 'set-device-owner', ADMIN)
            if 'Success' not in result:
                raise RuntimeError('Real Device Owner provisioning failed: ' + result)
        result = subprocess.run(
            ['adb', 'shell', 'am', 'instrument', '-w', '-r', '-e', 'class', case, RUNNER],
            capture_output=True, text=True, timeout=120,
        )
        raw = result.stdout + result.stderr
        ok = (result.returncode == 0 and bool(re.search(r'OK \(1 tests?\)', raw))
              and len(re.findall(r'INSTRUMENTATION_STATUS_CODE: 0\b', raw)) == 1
              and not re.search(r'INSTRUMENTATION_STATUS_CODE: -(?:1|2|3|4)\b', raw)
              and 'INSTRUMENTATION_FAILED' not in raw and 'FAILURES!!!' not in raw)
        if not ok:
            error = f'Incomplete or unsuccessful native test (exit {result.returncode})'
    except subprocess.TimeoutExpired as timeout:
        stdout = timeout.stdout or b''
        raw = stdout.decode(errors='replace') if isinstance(stdout, bytes) else stdout
        error = '120-second per-test deadline exceeded; NOT retried'
    except Exception as failure:
        error = str(failure)
    finally:
        (OUT / (label + '-instrumentation.txt')).write_text(raw + '\n' + error)
        if not ok:
            evidence(label)
        try:
            adb('pull', f'/sdcard/Android/data/{PACKAGE}/files/qa/.', str(OUT), check=False)
            adb('shell', 'am', 'force-stop', PACKAGE)
            if owner:
                removal = adb('shell', 'dpm', 'remove-active-admin', ADMIN)
                if 'Success' not in removal:
                    ok = False
                    error += '\nDevice Owner cleanup failed: ' + removal
        except Exception as failure:
            ok = False
            error += '\nCleanup failed: ' + str(failure)
    summary = dict(test=case, passed=ok, seconds=round(time.monotonic() - start, 2), error=error)
    print(json.dumps(summary), flush=True)
    return summary

def main() -> int:
    api = int(sys.argv[1])
    tests = json.loads(Path('device-tests/inventory.json').read_text())
    if len(tests) < 46 or len(set(tests)) != len(tests):
        raise ValueError('Invalid native inventory')
    ordinary = [t for t in tests if '.DeviceOwnerPolicyTest#' not in t]
    owner = [t for t in tests if '.DeviceOwnerPolicyTest#' in t]
    if len(owner) < 3:
        raise ValueError('Missing Device Owner regression journeys')
    jobs = [(t, False) for t in ordinary]
    # Explicit stress repetitions: every attempt is required to pass, never replacing a failure.
    if api == 36:
        admin = next(t for t in ordinary if '#actualPinThenAdminActivationReturnsHomeWithoutAnotherPin' in t)
        jobs += [(admin, False), (admin, False)]
    jobs += [(t, True) for t in owner]
    results = []
    with (OUT / 'logcat-full.txt').open('w') as stream:
        logcat = subprocess.Popen(['adb', 'logcat', '-b', 'all', '-v', 'threadtime'], stdout=stream, stderr=stream)
        try:
            for index, (case, is_owner) in enumerate(jobs, 1):
                results.append(run_case(case, index, is_owner))
                (OUT / 'summary.json').write_text(json.dumps(dict(api=api, expected=len(jobs), results=results), indent=2))
        finally:
            logcat.terminate()
            try:
                logcat.wait(timeout=5)
            except subprocess.TimeoutExpired:
                logcat.kill()
    passed = sum(r['passed'] for r in results)
    Path('instrumentation.txt').write_text(f'{passed}/{len(jobs)} native invocations passed; {len(tests)} unique tests, no skipped cases.\n')
    return 0 if passed == len(jobs) else 1

if __name__ == '__main__':
    sys.exit(main())
