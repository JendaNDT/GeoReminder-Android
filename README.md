# GeoReminder (Android)

Nativní Android verze GeoReminderu – **„Připomeň mi X, až budu u Y / v 18:30."**
Vzhled 1:1 podle iOS verze (viz `design-podklady/DESIGN_SPEC.md` v iOS projektu).

Stack: Kotlin + Jetpack Compose, Google Maps, GeofencingClient, AlarmManager, Glance widget. Minimum: Android 8 (API 26).

## Jak sestavit APK

Nejjednodušší: **přilož tenhle projekt (zip) do session s Claudem** a napiš, co je potřeba. Claude umí projekt sestavit v cloudu a poslat hotové APK.

Ručně přes Android Studio:
1. Otevři tuhle složku v Android Studiu (Open → vybrat složku projektu).
2. Počkej, až se stáhnou závislosti (poprvé pár minut).
3. Build → Generate App Bundles or APKs → Generate APKs.
4. Hotové APK: `app/build/outputs/apk/release/app-release.apk`.

## Důležité soubory

- `mapskey.properties` – Google Maps klíč (`MAPS_API_KEY=AIza…`). Bez něj se appka sestaví, ale mapa bude prázdná.
- `app/georeminder.keystore` – podpisový klíč (hesla `georeminder`). **Neztratit!** Jen s ním jdou aktualizace instalovat přes starší verzi.
- `PROJECT_STATUS.md` – aktuální stav projektu; přilož ho na začátku další session s Claudem.
