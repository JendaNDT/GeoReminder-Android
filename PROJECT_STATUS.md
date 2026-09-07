# GeoReminder Android – Project Status

**Aktualizováno:** 8. 9. 2026  
**Aktuální build:** versionName **2.7**, versionCode **19**  
**SDK:** minSdk 26, compileSdk 36, targetSdk 36  
**Větev stabilizace:** `fix/stabilizace-etapa-1`  
**PR:** #1, stále draft do dokončení device/Play release gate.

Tento soubor je aktuální zdroj pravdy pro stav projektu. `AUDIT1.md` a `AUDIT3.md` jsou historické snapshoty starších verzí. Detailní plán je v `IMPLEMENTACNI-PLAN-OPRAV-2026-09.md`; autoritativní stav jeho dokončení je v `IMPLEMENTACNI-PLAN-OPRAV-2026-09-COMPLETION.md`.

## Aktuální funkce

GeoReminder podporuje:

- location remindery přes geofencing (příjezd/odjezd),
- time remindery jednorázové/denní/týdenní,
- Snooze s perzistentním stavem,
- notifikační akce a deep-link do správného reminderu,
- TTS a dožadování,
- mapu, Photon hledání + Geocoder fallback,
- oblíbená místa,
- CZ/EN/SYSTEM per-app locale,
- import z CalendarContract.Instances s deduplikací,
- JPEG/PNG/PDF přílohy,
- ZIP backup včetně příloh + import staršího JSON backupu,
- Glance widget seřazený podle nejbližších reminderů,
- diagnostiku oprávnění, plánování, geofence a datového stavu.

## Stabilizační etapy

### Etapy 1–9
Implementované:

- awaitable načítání dat a cold-start safe receivery,
- centralizovaný scheduler a stabilní requestCode/PendingIntent kontrakt,
- opravený Snooze a repeating time logika,
- target API 36 + exact/background permission flow,
- per-reminder geofence stav a deterministický limit 100,
- corrupted JSON recovery, sandbox příloh a bezpečný backup/import,
- notification deep-links, idempotentní akce a TTS lifecycle,
- Calendar Instances, deduplikace importu, Photon error states a widget ordering,
- AppCompat per-app locales, CZ/EN resources, dirty editor protection a MIME policy příloh.

### Etapa 10 – automatické testy
Automatická část implementovaná a ověřená.

- JVM testy pokrývají scheduler math, DST, přechod roku, storage recovery, Apple date kompatibilitu, backup/import, geofence policy, resolver, localization a další kritickou logiku.
- Android instrumentation pokrývá mimo jiné scheduler/snooze/PendingIntent stav, cold-start receivery a permission/system-access scénáře.
- GitHub Actions run #166 (`34162208388`) úspěšně dokončil build i API 36 instrumentation job.
- Povinné reálné scénáře zůstávají v `DEVICE-TEST-MATRIX.md` a nejsou vydávány za automaticky ověřené.

### Etapa 11 – diagnostika
Implementovaná.

`Nastavení → Spolehlivost → Diagnostika připomínek` zobrazuje stav notifikací, fine/background location, systémové polohy, exact alarms, battery optimization, počty geofence/time reminderů, nejbližší alarm, snooze, poslední resync, poslední geofence chybu a stav dat.

Diagnostika drží maximálně 50 technických událostí a záměrně neukládá názvy reminderů, jejich ID ani GPS souřadnice. Umí vytvořit anonymizovaný technický report, spustit resync a založit testovací reminder za jednu minutu.

### Etapa 12 – release hardening
Implementovaná v kódu a automatizační vrstvě.

- release lint je zapnutý (`checkReleaseBuilds = true`, `abortOnError = true`),
- neminifikovaný release + lint prošel v runu #170 (`34165302459`),
- R8 je zapnutý (`isMinifyEnabled = true`),
- izolovaný minifikovaný release + lint prošel build krokem runu #186 (`34165752970`),
- CI ověřuje unit testy, release lint, debug/release build, `bundleRelease`, instrumentation APK a API 36 instrumentation,
- workflow běží pro PR do `main`, push do `main` a ruční `workflow_dispatch`,
- signing hesla už nejsou natvrdo v aktivní Gradle konfiguraci; čtou se z ignorovaného `app/keystore.properties` nebo environment proměnných,
- dokumentace, Play checklist, store texty a privacy policy jsou synchronizované se skutečným buildem a aktuální politikou.

> R8 runtime smoke na fyzickém zařízení zůstává release gate. Z úspěšného sestavení nelze odvodit, že minifikovaný TTS, receivery, Glance widget, backup/import a Maps/Play Services byly fyzicky vyzkoušené.

## CI

Workflow: `.github/workflows/verify-stabilization.yml`

Používá:

- JDK 17,
- Android SDK/API 36,
- Gradle 8.13.

Kontroly:

- `:app:testDebugUnitTest`
- `:app:lintRelease`
- `:app:assembleDebug`
- `:app:assembleRelease`
- `:app:bundleRelease`
- `:app:assembleDebugAndroidTest`
- API 36 emulator instrumentation.

## Data a bezpečnost

- Reminder/favorites data se čtou a zapisují mimo UI thread.
- Fatal corruption se automaticky nepřepisuje prázdným stavem.
- Partial recovery zachová platné záznamy a stav je viditelný v diagnostice.
- Attachment paths jsou omezené na spravovaný sandbox.
- Backup ZIP má limity velikosti/počtu entries a ochranu proti path traversal.
- Podporované přílohy: JPEG, PNG a PDF.
- Diagnostický export neobsahuje osobní názvy reminderů ani přesné souřadnice.
- Keystore, `keystore.properties` a Maps API key zůstávají mimo Git.

## Android oprávnění

Manifest používá:

- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`
- `ACCESS_BACKGROUND_LOCATION`
- `POST_NOTIFICATIONS`
- `SCHEDULE_EXACT_ALARM`
- `RECEIVE_BOOT_COMPLETED`
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- `READ_CALENDAR`
- `INTERNET`

GeoReminder používá uživatelsky udělovaný `SCHEDULE_EXACT_ALARM`, nikoli omezené `USE_EXACT_ALARM`.

## Co stále blokuje veřejný release

- dokončit reálnou device matrix, zejména Samsung/One UI a čistý Android,
- ověřit minifikovaný build na zařízení: persistence/serialization, TTS, receivery, widget, backup/import a Maps/Play Services,
- pokud skutečný keystore stále používá historicky zveřejněné heslo, změnit jej lokálně nebo keystore považovat za kompromitovatelný při úniku souboru,
- dokončit Google Play formuláře a closed testing podle aktuálních podmínek konkrétního developer účtu,
- nahrát správně podepsaný release `.aab` s vhodným novým versionCode,
- ověřit store listing a privacy/Data safety deklarace proti finálnímu release kandidátovi.

## Související dokumenty

- `IMPLEMENTACNI-PLAN-OPRAV-2026-09.md` – detailní specifikace stabilizačního plánu,
- `IMPLEMENTACNI-PLAN-OPRAV-2026-09-COMPLETION.md` – autoritativní stav dokončení Etap 1–12,
- `DOCUMENTATION-STATUS.md` – hierarchie a stav dokumentů,
- `DEVICE-TEST-MATRIX.md` – manuální/device release gate,
- `GOOGLE-PLAY-CHECKLIST.md` – Play policy/release checklist,
- `PRIVACY.md` – privacy policy,
- `AUDIT1.md`, `AUDIT3.md` – historické audity.
