#!/usr/bin/env python3
import re
from pathlib import Path
import xml.etree.ElementTree as ET
from native_inventory import inventory
root = Path(__file__).resolve().parents[1]
android = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(root/'app/src/main/AndroidManifest.xml').getroot()
application = manifest.find('application')
assert application.get(android+'allowBackup') == 'false'
assert application.get(android+'dataExtractionRules') == '@xml/data_extraction_rules'
assert application.get(android+'testOnly') is None
required = {'root','file','database','sharedpref','external','device_root','device_file','device_database','device_sharedpref'}
backup = ET.parse(root/'app/src/main/res/xml/backup_rules.xml').getroot()
assert {e.get('domain') for e in backup.findall('exclude') if e.get('path') == '.'} == required
transfer = ET.parse(root/'app/src/main/res/xml/data_extraction_rules.xml').getroot()
for mode in ('cloud-backup','device-transfer'):
    assert {e.get('domain') for e in transfer.find(mode).findall('exclude') if e.get('path') == '.'} == required
production = '\n'.join(p.read_text() for p in (root/'app/src/main/java').rglob('*.java'))
assert 'BOOTSTRAP_HASH' not in production and 'BOOTSTRAP_SALT' not in production
assert 'TestCredentials' not in production and '826493' not in production
assert 'setHideOverlayWindows(true)' in production
assert 'HIDE_OVERLAY_WINDOWS' in (root/'app/src/main/AndroidManifest.xml').read_text()
assert 'targetSdk 36' in (root/'app/build.gradle').read_text()
assert 'compileSdk 36' in (root/'app/build.gradle').read_text()
adb_allowed = {'PermissionUtils.java','DiagnosticReport.java'}
for source in (root/'app/src/main/java').rglob('*.java'):
    if source.name not in adb_allowed:
        assert 'isAdbEnabled(' not in source.read_text(), f'ADB must remain informational, enforcement found in {source}'
assert 'Le débogage ADB est actif et peut contourner Bernard' not in (root/'app/src/main/java/com/local/focusfence/service/FocusAccessibilityService.java').read_text()
assert 'Désactive d’abord le débogage ADB' not in (root/'app/src/main/java/com/local/focusfence/ui/MainActivity.java').read_text()
assert len(inventory(root/'app/src/androidTest/java')) >= 65
print('Security invariants and complete native inventory verified')
