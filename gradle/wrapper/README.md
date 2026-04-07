# Gradle Wrapper JAR

The gradle-wrapper.jar file is not included in this repository.

When you push this to GitHub, the workflow will automatically:
1. Run `gradle wrapper` to generate the wrapper
2. Commit it to the repository
3. Build the APK and AAB files

Alternatively, if you have Gradle installed locally, run:
```bash
gradle wrapper
```

This will download and set up the gradle-wrapper.jar file.
