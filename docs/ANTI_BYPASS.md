# Bernard Bloqueur — anti-bypass threat model

## Intended threat model

Bernard is a normal sideloaded Android application using an AccessibilityService and Usage Access.
The hardening targets a normal, non-rooted primary Android user who can navigate the phone and
system settings but does not have ADB/root/device-owner privileges.

It is designed to make casual or deliberate in-phone bypasses inconvenient and PIN-gated. It is
not a replacement for Android Enterprise Device Owner management.

## Protected paths in v0.4.0

- Swiping Bernard away or closing its Activity does not stop the AccessibilityService.
- Limits, app selection, permissions page and settings require the administrator PIN.
- The PIN is configured on-device on first launch. It is not compiled into the APK or repository.
  Private storage contains only a random salt plus a PBKDF2-HMAC-SHA256 verifier.
- Five failed PIN attempts trigger a persisted progressive lockout: 30 seconds, then 2 minutes, 10 minutes, 1 hour and up to 6 hours for repeated bursts.
- While Bernard is active, Android/OEM Settings are PIN-gated as a whole. This closes App Info, Accessibility, Usage Access, Date & time, battery and equivalent OEM control paths instead of relying on brittle per-screen detection.
- Package-installer / Play Store control surfaces are PIN-gated when they visibly target Bernard.
- Removing Usage Access makes finite game/app quotas fail closed rather than silently disabling them.
- Accessibility shortcut assignments for Bernard are treated as an anti-tamper condition when the
  Android build exposes those settings to third-party apps.
- Manual clock/time-zone changes trigger anti-tamper state.
- Instagram Feed, Explore, Reels, Stories, hashtag/search grids and post-detail consumption share
  the configured scroll budget. DMs, comments, creation, notifications and profile shells remain
  usable.
- Facebook Feed/Reels/Stories and Watch-style surfaces are controlled when detectable.
- YouTube Shorts, TikTok and Threads are part of the scroll budget.
- Instagram Lite and Facebook Lite cannot be used as alternate-client bypasses.
- Installed browsers are discovered dynamically. Social web URLs for Instagram, Facebook,
  YouTube Shorts, TikTok and Threads are mapped back into the same budget.
- Obvious renamed/clone clients are treated as whole-app social surfaces.
- The app has no INTERNET permission and Android backup is disabled.
- Production releases use one pinned release-signing certificate; CI refuses a main-branch APK
  signed with another certificate.

## Deliberate usability exceptions

Instagram Direct Messages remain available even when Instagram's consumption surfaces are blocked.
Profile shells, comments, notifications and creation tools are also allowed. Opening actual content
from those utility surfaces can still be classified into the scroll budget.

## Residual bypasses a normal app cannot eliminate

A device owner can still defeat a non-Device-Owner app with sufficiently privileged/system-level
actions, including safe mode on devices that disable third-party apps there, ADB/root, factory reset,
some secondary-user/work-profile arrangements, or OEM/system behavior that disables an
AccessibilityService before it can intervene.

For materially stronger guarantees, Bernard would need an Android Enterprise Device Policy
Controller provisioned as Device Owner on a managed device. Device Owner APIs can block uninstall
and user control in ways a normal accessibility app cannot.

## Provisioning rule

Configure the administrator PIN before handing the device to the restricted user. Keep the release
keystore and GitHub Actions signing secrets private and backed up. Losing the release key means
future APKs cannot update the installed production app in place.
