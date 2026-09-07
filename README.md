# GeoReminder (Android)

Nativní Android aplikace pro připomínky **na místě** a **v čase**: „Připomeň mi X, až budu u Y / v 18:30.“

**Aktuální build:** versionName **2.7**, versionCode **19**  
**Platforma:** minSdk 26 (Android 8), compileSdk 36, targetSdk 36 (Android 16)  
**Stack:** Kotlin, Jetpack Compose, AppCompat per-app locales, Google Maps/Geofencing, AlarmManager, Glance widget, kotlinx.serialization.

> Aktuální technický stav je v `PROJECT_STATUS.md`. Autoritativní completion záznam stabilizačních Etap 1–12 je v `IMPLEMENTACNI-PLAN-OPRAV-2026-09-COMPLETION.md`. Historické soubory `AUDIT*.md` popisují starší snapshoty a nejsou zdrojem pravdy pro současnou kódovou bázi.

## Co aplikace umí

- připomínky na místo: příjezd/odjezd, poloměr, jednorázové i opakované geofence,
- časové připomínky: jednorázově, denně nebo týdně ve vybraných dnech,
- Snooze bez přepisování původního času/pravidla reminderu,
- notifikační akce Hotovo, Odložit, Zítra ráno a Navigovat,
- tiché/výchozí/naléhavé upozornění a volitelné dožadování,
- hledání míst přes Photon s Geocoder fallbackem,
- sdílení míst z map a zpracování `geo:` URI,
- oblíbená místa a mapový přehled,
- JPEG/PNG/PDF přílohy uložené v sandboxu aplikace,
- jednorázový import událostí z kalendáře,
- TTS,
- Glance widget,
- CZ/EN/SYSTEM per-app jazyk,
- ZIP backup včetně podporovaných příloh + kompatibilní import starého JSON formátu,
- diagnostiku spolehlivosti: oprávnění, geofence, alarmy, snooze, stav dat a anonymizovanou technickou historii.

## Spolehlivost a data

Stabilizační větev řeší cold-start, reboot, idempotentní resync, stabilní PendingIntenty, perzistentní snooze/fired stav, geofence limit 100, partial recovery poškozeného JSONu, sandboxované attachment paths a bezpečný ZIP import.

Receivery čekají na skutečné načtení dat před doručením nebo resyncem. Poškozený datový soubor se automaticky nepřepisuje prázdným stavem. Geofence registrační chyby se evidují po jednotlivých reminderech a jsou viditelné v diagnostice.

## Build a testy

Projekt používá JDK 17 a Gradle 8.13. Release variant má zapnutý R8 (`isMinifyEnabled = true`) a release lint může build zastavit při chybě.

Lokálně lze spustit například:

```bash
gradle :app:testDebugUnitTest :app:lintRelease :app:assembleDebug :app:assembleRelease :app:bundleRelease :app:assembleDebugAndroidTest
```

CI workflow `.github/workflows/verify-stabilization.yml` běží pro PR do `main`, po sloučení pro push do `main` a ručně přes `workflow_dispatch`. Kromě JVM testů sestavuje debug variantu, minifikovaný release APK i release AAB, spouští `lintRelease` a následně Android instrumentation testy na API 36 emulátoru.

Automatické API 36 instrumentation testy prošly v runu #166. Neminifikovaný release + lint prošel v runu #170 a minifikovaný R8 release build v runu #186.

Manuální scénáře, které nelze poctivě prohlásit za ověřené jen z CI, jsou v `DEVICE-TEST-MATRIX.md`. Patří sem i fyzický smoke test minifikovaného release buildu pro TTS, receivery, Glance widget, backup/import a Maps/Play Services.

## Sestavení a podpis

1. Otevřít projekt v Android Studiu nebo použít Gradle 8.13 + JDK 17.
2. Pro funkční Google Maps doplnit `mapskey.properties` v kořeni projektu:
   `MAPS_API_KEY=...`
3. Podpisový soubor `app/georeminder.keystore` je záměrně mimo Git.
4. Podpisová hesla se nedrží v `build.gradle.kts`. Lokálně vytvořit `app/keystore.properties` podle `app/keystore.properties.example`, případně použít bezpečné environment proměnné.
5. Debug APK: `app/build/outputs/apk/debug/`.
6. Play artifact: `app/build/outputs/bundle/release/` po `bundleRelease`.

Bez `mapskey.properties` se projekt sestaví s placeholderem, ale mapa nebude funkční. Bez keystore a signing credentials CI stále ověří unsigned release build/bundle, ale produkční upload musí být podepsaný platným upload key.

Historická Git data mohou obsahovat dřívější signing heslo. Samotný keystore v Gitu nikdy být nemá; pokud stále používá historicky zveřejněné heslo, je vhodné jej před veřejným vydáním lokálně změnit nebo keystore považovat za vysoce citlivý při případném úniku.

## Důležité dokumenty

- `PROJECT_STATUS.md` – aktuální technický stav a zbývající release blokery,
- `IMPLEMENTACNI-PLAN-OPRAV-2026-09.md` – detailní stabilizační plán,
- `IMPLEMENTACNI-PLAN-OPRAV-2026-09-COMPLETION.md` – skutečný stav dokončení Etap 1–12,
- `DOCUMENTATION-STATUS.md` – hierarchie aktuálních/historických dokumentů,
- `DEVICE-TEST-MATRIX.md` – povinné reálné/emulátorové scénáře,
- `GOOGLE-PLAY-CHECKLIST.md` – Play Console a policy checklist,
- `PRIVACY.md` – zásady ochrany soukromí odpovídající současnému chování,
- `AUDIT1.md`, `AUDIT3.md` – historické audity starších verzí.

## Struktura kódu

- `model/` – datové modely, serializace a locale-aware formátování,
- `data/` – úložiště, backup/import, poloha, diagnostika, nastavení a resolver míst,
- `notify/` – scheduler, alarm/geofence receivery, notifikace, TTS,
- `ui/` – Compose obrazovky, editor, nastavení a diagnostika,
- `widget/` – Glance widget.

## Stav vydání

**Implementační Etapy 1–12 jsou dokončené v kódu a automatizační vrstvě.** Veřejný release ale ještě není považovaný za hotový, dokud neprojde povinná device matrix, fyzický smoke test minifikovaného release buildu a Google Play closed/release proces. PR proto zůstává draft.
