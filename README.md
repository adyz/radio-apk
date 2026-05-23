# Coji Android TV

Minimal Android TV / Google TV wrapper for https://coji.ro.

## Deschidere
1. Instalează Android Studio.
2. Open project -> selectează folderul `coji-android-tv`.
3. Lasă Gradle sync să termine.
4. Rulează pe emulator Android TV sau direct pe Google TV / XGIMI cu USB debugging.

## Build APK debug
Din terminal, în folderul proiectului:

```bash
./gradlew assembleDebug
```

Pe Windows:

```bat
gradlew.bat assembleDebug
```

APK-ul apare în:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Pentru Play Store
Pentru publicare în Play Console trebuie AAB:

```bash
./gradlew bundleRelease
```

Mai trebuie semnare release în Android Studio:
Build -> Generate Signed Bundle / APK -> Android App Bundle.

## Important pentru TV
Site-ul trebuie să fie navigabil cu telecomanda:
- focus vizibil pe carduri/butoane
- DPAD sus/jos/stânga/dreapta
- OK/Enter pentru play
- Back previzibil
