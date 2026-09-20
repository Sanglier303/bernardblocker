# Credits and third-party notices

The application source is provided under GPL-3.0; see LICENSE.

The inherited ShortSurfaceDetector centralizes accessibility identifiers and structural approaches adapted in the original prototype from:
- Nudge: https://github.com/astraedus/nudge
- Scrolless: https://github.com/duartebarbosadev/Scrolless

These upstream projects are not dependencies downloaded at runtime. Their approach does not constitute a guarantee that a particular current Instagram/Facebook/YouTube release is supported.

Gradle Wrapper: copyright the Gradle authors, Apache-2.0. Its distribution retains its embedded LICENSE. JUnit and AndroidX are build/test dependencies resolved by Gradle, not repackaged as standalone third-party source. Android system fonts are used; no third-party font files are distributed.

Bernard artwork: the character reference and generated illustrations supplied and approved in the conversation for this personal project. No stock boar is substituted. The project does not grant rights to third-party app trademarks. App icons in the app selector are loaded locally from Android PackageManager.

Official technical references:
- https://developer.android.com/build/releases/agp-8-7-0-release-notes
- https://developer.android.com/reference/android/app/usage/UsageStatsManager
- https://developer.android.com/reference/android/app/usage/UsageEvents.Event
- https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY
- https://developer.android.com/develop/ui/views/layout/edge-to-edge
- https://github.com/ReactiveCircus/android-emulator-runner
