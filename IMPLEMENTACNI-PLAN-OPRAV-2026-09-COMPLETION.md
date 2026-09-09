# GeoReminder – dokončení implementačního plánu oprav 2026-09

**Aktualizováno:** 8. 9. 2026  
**Doplňuje:** `IMPLEMENTACNI-PLAN-OPRAV-2026-09.md`

Tento soubor je autoritativní záznam **stavu realizace** původního detailního plánu. Původní dokument zůstává beze ztráty detailů jako specifikace jednotlivých etap; tento completion záznam má přednost tam, kde jeho starší horní stav ještě tvrdí, že Etapy 10–12 zbývají.

## Souhrnný stav

| Etapa | Stav |
|---|---|
| 1 – cold-start / načítání dat | ✅ implementováno |
| 2 – scheduler / idempotentní resync | ✅ implementováno |
| 3 – time reminders / snooze | ✅ implementováno |
| 4 – API 36 / exact + background access | ✅ implementováno |
| 5 – geofence registrace / limit 100 | ✅ implementováno |
| 6 – data recovery / backup / attachment safety | ✅ implementováno |
| 7 – notifikace / deep-link / TTS | ✅ implementováno |
| 8 – kalendář / resolver / widget | ✅ implementováno |
| 9 – lokalizace / UI konzistence | ✅ implementováno |
| 10 – automatické testy kritické cesty | ✅ automatická část implementována; device matrix zůstává release gate |
| 11 – diagnostika spolehlivosti | ✅ implementováno |
| 12 – release hardening a dokumentace | ✅ implementováno v kódu/CI; reálný device + Play release gate zůstává |

## Etapa 10 – důkaz automatického ověření

GitHub Actions run **#166** (`34162208388`) úspěšně dokončil:

- JVM testy,
- debug build,
- instrumentation APK,
- boot API 36 emulátoru,
- Android instrumentation test suite.

Instrumentation testy ověřují kritické systémové cesty bez čekání na reálné minuty/hodiny, včetně cold-start receiverů, scheduler/snooze/PendingIntent stavů a vybraných permission/system-access scénářů.

Manuální scénáře jsou vedené v `DEVICE-TEST-MATRIX.md`. Zelený emulátor nenahrazuje Samsung/One UI a referenční čistý Android.

## Etapa 11 – diagnostika

Implementována obrazovka `Nastavení → Spolehlivost → Diagnostika připomínek`.

Obsahuje:

- notifikace,
- fine/background location,
- systémovou polohu,
- exact alarm access,
- battery optimization,
- počty aktivních geo/time reminderů,
- registrované/neúspěšné geofence a limit 100,
- nejbližší časový alarm,
- počet snooze,
- poslední úspěšný resync,
- poslední historickou chybu geofence,
- stav datového souboru.

Akce:

- otevřít konkrétní systémové nastavení podle problému,
- resync,
- testovací reminder +1 min,
- anonymizovaný technický report do schránky.

Diagnostický ring-buffer má maximálně 50 technických událostí a neukládá názvy reminderů, jejich ID ani přesné GPS souřadnice.

## Etapa 12 – release hardening

### Release lint

Zapnuto:

- `checkReleaseBuilds = true`
- `abortOnError = true`

První neminifikovaný hardening průchod **run #170** (`34165302459`) dokončil úspěšně:

- unit testy,
- `lintRelease`,
- debug build,
- release build,
- instrumentation APK.

Žádné globální vypnutí lint kontrol nebylo použito.

### R8

Po zeleném neminifikovaném release byl zapnut `isMinifyEnabled = true`.

Izolovaný R8 průchod **run #186** (`34165752970`) úspěšně dokončil krok:

- unit testy,
- `lintRelease`,
- debug build,
- **minifikovaný `assembleRelease`**.

Serializovatelné modely mají explicitní keep rules; knihovny Compose/Glance/Play Services používají své consumer rules.

> R8 runtime smoke na fyzickém zařízení zůstává povinný před veřejným releasem. Ze samotného sestavení nelze poctivě prohlásit za ověřené TTS, receivery, Glance widget, backup/import a Maps/Play Services v minifikovaném artefaktu.

### CI

Workflow `.github/workflows/verify-stabilization.yml` je nastaven pro:

- `pull_request` do `main`,
- `push` do `main`,
- ruční `workflow_dispatch`.

Kontroluje:

- `testDebugUnitTest`,
- `lintRelease`,
- `assembleDebug`,
- `assembleRelease`,
- `bundleRelease`,
- `assembleDebugAndroidTest`,
- API 36 emulator instrumentation.

Debug APK + instrumentation APK jsou mezi joby předávány jako artifact, takže emulator job aplikaci znovu nekompiluje.

### Signing secrets

Aktivní build konfigurace už neobsahuje heslo ke keystore natvrdo.

Signing credentials se načítají z ignorovaného `app/keystore.properties` nebo z environment proměnných:

- `GEOREMINDER_STORE_PASSWORD`
- `GEOREMINDER_KEY_ALIAS`
- `GEOREMINDER_KEY_PASSWORD`

V repozitáři je pouze `app/keystore.properties.example`.

Historická Git data mohou obsahovat dřívější hodnotu hesla; samotný keystore zůstává mimo Git a je třeba s ním zacházet jako s citlivým tajemstvím.

### Dokumentace

Aktualizováno/sjednoceno:

- `README.md`,
- `PROJECT_STATUS.md`,
- `GOOGLE-PLAY-CHECKLIST.md`,
- `GOOGLE-PLAY-TEXTY.md`,
- `PRIVACY.md`,
- `DOCUMENTATION-STATUS.md`.

`AUDIT*.md` jsou vedené jako historické snapshoty; starší implementační plány jsou označené v `DOCUMENTATION-STATUS.md` jako nahrazené/odložené.

### Google Play hardening

Ověřený stav dokumentace k 8. 9. 2026:

- targetSdk 36,
- aplikace používá `SCHEDULE_EXACT_ALARM`, nikoli omezené `USE_EXACT_ALARM`,
- background location má samostatný Play declaration/review gate,
- Data safety se musí vyplnit podle aktuální Play definice collected/shared včetně třetích stran,
- privacy policy odpovídá současnému chování,
- store listing byl aktualizován na build 2.7/API36 a podporované JPEG/PNG/PDF přílohy.

## Definition of Done – aktuální stav

- [x] targetSdk 36
- [x] release build projde bez minifikace
- [x] release lint projde bez globálního ignorování
- [x] R8/minifikovaný release se sestaví
- [x] současné unit testy jsou zelené
- [x] kritické API 36 instrumentation testy jsou zelené
- [x] cold-start receivery čekají na načtená data
- [x] snooze blokuje původní trigger
- [x] repeating edit nemění čas bez zásahu uživatele
- [x] exact/background access je viditelný a opravitelný
- [x] per-reminder geofence failure
- [x] corrupted JSON recovery
- [x] sandbox příloh + definovaný backup formát
- [x] notifikační deep-link
- [x] calendar import deduplikace
- [x] CZ/EN resources
- [x] diagnostická obrazovka
- [x] `PROJECT_STATUS.md` odpovídá buildu 2.7 / 19 / target 36
- [ ] minifikovaný release runtime smoke na fyzickém zařízení
- [ ] restart/geofence/time/battery/permission matrix na Samsung/One UI
- [ ] referenční čistý Android/Pixel device gate
- [ ] closed testing / Play Console declarations podle konkrétního developer účtu

## Závěr

**Implementační Etapy 1–12 jsou dokončené v kódu a automatizační vrstvě.** Veřejné vydání ale ještě není označeno za hotové, dokud neprojde reálná device matrix, minifikovaný runtime smoke a Google Play release proces.
