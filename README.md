# GeoReminder (Android)

Nativní Android aplikace pro připomínky **na místě** a **v čase**: „Připomeň mi X, až budu u Y / v 18:30.“

**Aktuální build:** versionName **2.7**, versionCode **19**  
**Platforma:** minSdk 26 (Android 8), compileSdk 36, targetSdk 36 (Android 16)  
**Stack:** Kotlin, Jetpack Compose, AppCompat per-app locales, Google Maps/Geofencing, AlarmManager, Glance widget, kotlinx.serialization.

> Aktuální technický stav je v `PROJECT_STATUS.md`. Historické soubory `AUDIT*.md` popisují starší snapshoty a nejsou zdrojem pravdy pro současnou kódovou bázi.

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

Projekt používá JDK 17 a Gradle 8.13.

Lokálně lze spustit například:

```bash
gradle :app:testDebugUnitTest :app:lintRelease :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest
```

CI workflow `.github/workflows/verify-stabilization.yml` běží pro stabilizační větev, PR do `main` a po sloučení také pro push do `main`. Kromě JVM testů sestavuje debug/release build, spouští release lint a následně Android instrumentation testy na API 36 emulátoru.

Manuální scénáře, které nelze poctivě prohlásit za ověřené jen z CI, jsou v `DEVICE-TEST-MATRIX.md`.

## Sestavení

1. Otevřít projekt v Android Studiu nebo použít Gradle 8.13 + JDK 17.
2. Pro funkční Google Maps doplnit `mapskey.properties` v kořeni projektu:
   `MAPS_API_KEY=...`
3. Podpisový soubor `app/georeminder.keystore` je záměrně mimo Git.
4. Debug APK: `app/build/outputs/apk/debug/`.
5. Release APK/AAB pro Play sestavovat až s platným upload key a po dokončení release checklistu.

Bez `mapskey.properties` se projekt sestaví s placeholderem, ale mapa nebude funkční. Bez produkčního keystore nelze vytvořit release podepsaný stejným upload key jako existující distribuce.

## Důležité dokumenty

- `PROJECT_STATUS.md` – aktuální technický stav a zbývající release blokery,
- `IMPLEMENTACNI-PLAN-OPRAV-2026-09.md` – stabilizační plán a Definition of Done,
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

Kód je na stabilizační větvi připravený pro API 36 a automatické testování. Veřejný release není považovaný za hotový, dokud neprojde release lint/build, R8 hardening a povinná device matrix zejména na Samsung/One UI a čistém Androidu. PR zůstává do té doby draft.
