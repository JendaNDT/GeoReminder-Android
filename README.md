# GeoReminder (Android)

Nativní Android verze GeoReminderu – **„Připomeň mi X, až budu u Y / v 18:30."**
Vzhled 1:1 podle iOS verze (viz `design-podklady/DESIGN_SPEC.md` v iOS projektu), plus vlastní vylepšení navrch.

**Aktuální verze: 2.8** (versionCode 20) · Stack: Kotlin + Jetpack Compose, Google Maps, GeofencingClient, AlarmManager, Glance widget · Minimum: Android 8 (API 26), target 36

> **Aktuální stav:** viz `PROJECT_STATUS.md` a `AUDIT4.md`. Kritické nálezy AUDIT4 jsou opravené a debug/release gate prochází; před veřejným vydáním ještě zbývá terénní ověření alarmů, geofence a chování konkrétních výrobců telefonů.

## Co appka umí

- Připomínky **na místo** (příjezd/odjezd, poloměr 50–1000 m, opakování) a **na čas** (jednorázově / denně / týdně ve vybrané dny)
- **Druhy upozornění**: Tiché / Výchozí / Naléhavé (budíkový zvuk do zavření) + **dožadování** (nepotvrzená připomínka se vrací à 5 min)
- Tlačítka na notifikaci: Hotovo · Odložit o hodinu · Zítra ráno
- **Živé našeptávání míst** (Photon – i názvy podniků, vzdálenosti, ikony, poslední a oblíbená místa)
- **Sdílení místa z Map Google** rovnou do připomínky, příjem geo: odkazů
- **Přílohy** k připomínce (foto/PDF/soubor, kopie do appky), **hlasité čtení (TTS)**, **jednorázový import z Google Kalendáře** (volitelné v Nastavení)
- Oblíbená místa s čipy, mapový přehled připomínek
- **Widget** s tlačítkem +, rychlé akce ikony, přepínač vzhledu (systém/světlý/tmavý)
- Obnova hlídání po restartu telefonu
- Odolné asynchronní ukládání dat mimo hlavního UI vlákno, sanitace záloh a bezpečné mazání rozpracovaných příloh

## Jak sestavit a ověřit aplikaci

Projekt obsahuje Gradle Wrapper, takže není potřeba instalovat vlastní verzi Gradlu:

```bash
./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug bundleRelease
```

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Play bundle: `app/build/outputs/bundle/release/app-release.aab`
- Stejný gate běží také v `.github/workflows/android.yml`.

Pro podepsaný release nastav `GEOR_SIGNING_STORE_PASSWORD`, `GEOR_SIGNING_KEY_PASSWORD` a `GEOR_SIGNING_KEY_ALIAS` jako Gradle properties nebo proměnné prostředí. Bez nich vznikne bezpečně nepodepsaný release artefakt; debug vždy používá vlastní debug klíč.

## Důležité soubory

- `mapskey.properties` – Google Maps klíč (`MAPS_API_KEY=AIza…`). Bez něj se appka sestaví, ale mapa bude prázdná.
- `app/georeminder.keystore` – podpisový klíč. Jen s ním a odpovídajícími tajnými hodnotami jdou aktualizace instalovat přes starší verzi. Při nové verzi zvyš `versionCode` v `app/build.gradle.kts`.
- `PROJECT_STATUS.md` – aktuální stav projektu.
- `AUDIT4.md` – nejnovější komplexní audit včetně stavu následných oprav.

> **Pozor:** `mapskey.properties`, `app/georeminder.keystore` a signing hesla jsou záměrně **mimo git** (`.gitignore`). Bez produkčního podpisu nelze nový release nainstalovat jako aktualizaci stávající produkční instalace.

## Struktura kódu (`app/src/main/java/cz/jenda/georeminder/`)

- `model/` – datový model připomínky a oblíbeného místa, české formátování (JSON kompatibilní s iOS verzí)
- `data/` – úložiště (JSON soubory), poloha, poslední místa, rozluštění sdílených odkazů, přílohy, import kalendáře, změřené systémové lišty, nastavení funkcí
- `notify/` – notifikace a kanály, plánovač (geofence + budíky + dožadování), přijímače událostí, obnova po restartu, TTS speaker
- `ui/` – obrazovky (seznam, formulář, výběr místa, oblíbená, mapa, onboarding, nastavení, import kalendáře) + iOS-look komponenty a témata
- `widget/` – Glance widget „Nejbližší připomínky"
