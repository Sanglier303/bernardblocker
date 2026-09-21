# Permanent APK signing

Bernard production builds must use one permanent private release key.

## GitHub Actions secrets required

Repository settings -> Secrets and variables -> Actions -> New repository secret:

- BERNARD_SIGNING_KEYSTORE_B64
- BERNARD_SIGNING_STORE_PASSWORD
- BERNARD_SIGNING_KEY_ALIAS
- BERNARD_SIGNING_KEY_PASSWORD

The workflow decodes the keystore only on the main branch, signs the release APK and verifies that
its certificate matches SIGNING_CERTIFICATE.sha256. Pull-request and feature-branch builds never
receive the release key.

Never commit the JKS file or any of the four secret values.

## Recovery

Back up the release JKS and passwords offline. Losing the key means Android will not accept future
updates over an installed Bernard production APK with the same application id.

## Rotation

Do not replace the pinned certificate casually. A different certificate is intentionally treated as
a CI failure because it would reproduce the package-conflict problem seen during early debug builds.
