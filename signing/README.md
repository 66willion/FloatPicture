FloatPicture release signing files

- `floatpicture-release.jks`: release keystore used by Gradle `release` builds.
- `release-signing.properties`: default signing config read by `app/build.gradle`.

Build behavior:
- `release` uses `signing/release-signing.properties` by default.
- You can override any value with environment variables:
  `FLOATPICTURE_RELEASE_STORE_FILE`
  `FLOATPICTURE_RELEASE_STORE_PASSWORD`
  `FLOATPICTURE_RELEASE_KEY_ALIAS`
  `FLOATPICTURE_RELEASE_KEY_PASSWORD`

Operational notes:
- Back up `floatpicture-release.jks` before changing or rotating credentials.
- If this private repo is ever exposed, replace the keystore and passwords immediately.
