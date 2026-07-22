# GeoReminder Android – Project Status
*Naposled aktualizováno: 22. 07. 2026 (v2.5 – po komplexním auditu AUDIT2 + oprava zapojení)*

> **Poznámka k poctivosti:** tenhle soubor byl 22. 7. srovnán s realitou kódu na základě auditu `AUDIT2.md` (v kořeni `GeoReminder Android/`). Dřívější verze tohohle statusu tvrdila „hotové" funkce, které v kódu buď nebyly, byly rozbité, nebo jen zpola zapojené. Níže je stav, jak skutečně vypadá.

## 🎯 Co to je
Nativní Android verze GeoReminderu – připomínky vázané na místo i čas, vzhled 1:1 podle iOS předlohy (`design-podklady/DESIGN_SPEC.md`).
Stack: Kotlin + Jetpack Compose, Google Maps (Compose), GeofencingClient, AlarmManager (přesné budíky), Glance widget, App Shortcuts, JSON úložiště formátově kompatibilní s iOS. Minimum: Android 8 (API 26), target 35. Jeden modul.
**Verze: 2.5, versionCode 17.**

## ✅ Co funguje dobře (potvrzeno auditem)
- **Odolné ukládání dat:** atomický zápis (`AtomicFile`), čtení `reminders.json` po záznamech (jeden vadný záznam neshodí celý seznam), rozlišení „prázdno vs. chyba čtení" + pojistka proti přepsání platného souboru prázdným (`ReminderStore`/`SharedStorage`).
- **Spolehlivé doručení:** geofence (příjezd/odjezd, 50–1000 m, opakování) + časové připomínky (jednorázové/denní/týdenní) přes přesné budíky; catch-up zmeškaných budíků; obnova po restartu telefonu (`BootReceiver`); značky „vystřeleno" proti opakovanému střílení.
- **Robustní receivery** (`goAsync` + try/catch – chyba neshodí proces), serializované diskové IO (`limitedParallelism(1)`), deterministické formátování (`ThreadLocal`, `Locale cs_CZ`).
- **Notifikace:** 3 kanály (Tiché/Výchozí/Naléhavé), dožadování à 5 min, tlačítka Hotovo/Odložit/Zítra ráno; vlastní iOS-look komponenty; 4 témata (světlé/tmavé/neutrální/glass); widget „Nejbližší připomínky"; onboarding + oranžové bannery oprávnění; undo mazání (snackbar); živé našeptávání míst (Photon) s offline/prázdným stavem.
- **JSON bajtově kompatibilní s iOS** (stejná pole, UUID velkými, datumy jako sekundy od 1. 1. 2001).

## 🔧 Oprava zapojení (22. 7., po auditu)
Během předchozí práce mimo git se do pracovní složky nedostala některá zapojení z v2.4. Doplněno zpět:
- **`READ_CALENDAR`** v manifestu → import z kalendáře jde teď vůbec povolit (dřív systém oprávnění rovnou zamítl).
- **`<provider>` FileProvider + `res/xml/file_paths.xml`** → otevírání příloh funguje (dřív tiše padalo do prázdného catch).
- **`<queries>`** pro viditelnost map (Android 11+) + **try/catch** kolem navigačního intentu → „Navigovat" nespadne na zařízení bez mapové appky.
- **versionCode 12 → 17, versionName → 2.5.**
- **`isMinifyEnabled = false`** (R8 zatím vypnutý) – zapnout až po úspěšném testu na zařízení; jinak hrozí pády jen v release buildu, které v debugu nevidíš.

## ⚠️ Známé problémy / co ještě není hotové (detail v `AUDIT2.md`)
**Funkční / datové:**
- **Oblíbená místa** (`FavoritesStore`) nemají stejnou ochranu jako připomínky – po přechodné chybě čtení se můžou přepsat na prázdno (riziko ztráty). *(Doporučeno opravit brzy.)*
- **Import zálohy** duplikuje záznamy i se stejným ID (`BackupManager`).
- **Osiřelé přílohy** se po smazání připomínky neuklízejí; přílohy nejsou v řízené záloze; kopírují se **bez limitu velikosti** (deklarovaných 5 příloh / 10 MB neplatí – model drží jen 1 přílohu).
- **Import z kalendáře** vždy udělá jen časovou připomínku a **místo zahodí** (žádné geokódování), po importu bez potvrzení, kolik se naimportovalo.
- **Dialog „Zahodit změny?"** hlídá jen část polí – změna času/poloměru/opakování se může tiše ztratit.
- **`reload()` čte disk na hlavním vlákně** (riziko záseku u většího seznamu).

**Fantomové / nedodělané funkce:**
- **Seskupení notifikací na místě** – přepínač v Nastavení existuje, ale **nic nedělá** (implementace chybí).

**UI / přístupnost:**
- **Kontrast NEsplňuje WCAG AA** ve 3 ze 4 témat (šedé texty ~2,7:1, barevné čipy 2,0–3,4:1) – špatná čitelnost placeholderů a druhých řádků.
- **Anglická lokalizace fakticky nefunguje** – řetězce v `values-en` existují, ale UI je z ~90 % natvrdo česky (včetně enum labelů v modelu).
- **„Podle systému" + tmavý systém** = tmavé UI, ale světlá mapa.
- Dvojité horní odsazení u 3 sheetů; některé dotykové terče < 48 dp.

**Architektura / dluh:**
- **God files:** `EditReminderSheet.kt` (967 ř.), `ReminderListScreen.kt` (932), `LocationPickerSheet.kt` (721).
- **ViewModel** použit jen v jednom screenu, ostatní sahají přímo na singleton store.

## ⏭️ Doporučené příští kroky (dle AUDIT2)
1. **Sesynchronizovat s GitHubem** (build → push), ať je zase jeden zdroj pravdy. *(Řešeno 22. 7.)*
2. **Test na telefonu** (`NAVOD-INSTALACE.md`): časová i geo-připomínka v terénu, widget, přílohy, import kalendáře.
3. Opravit datová rizika: `FavoritesStore` guard, dedup importu zálohy, úklid osiřelých příloh.
4. Doladit **kontrast** (rychlá plošná oprava čitelnosti) a UX regrese (discard dialog, oprávnění na pozadí, vizuální odezva na dotek).
5. Rozhodnout u fantomových funkcí: **doimplementovat** (seskupení, víc příloh + limit), nebo **odstranit** polovičaté přepínače.
6. Google Play: účet + ověření, vyvěsit `privacy.html` (GitHub Pages), grafika (ikona 512, feature graphic, screenshoty), test 12 testerů/14 dní, zapnout R8 + `.aab`. Texty + privacy jsou hotové (`GOOGLE-PLAY-TEXTY.md`, `PRIVACY.md`).
7. Odložitelný dluh: rozdělení god-souborů, dotažení ViewModelu, přesun textů do `strings.xml`, plná lokalizace.

## 🏗️ Klíčová rozhodnutí (neměnit bez rozmyslu)
- **Výběr místa na mapě = celoobrazovkové okno**, ne sheet (tahy po mapě se praly s gestem zavírání). Ostatní obrazovky zůstávají sheety.
- **Hledání míst: Photon** (photon.komoot.io) primárně (zdarma, bez klíče, umí podniky); vestavěný `Geocoder` jen jako záloha.
- **Google Maps Demo klíč** (zdarma, bez karty); mapová vrstva izolovaná v `LocationPickerSheet`/`MapOverviewScreen` – jde vyměnit za MapLibre bez zásahu do zbytku.
- **Vlastní iOS-look komponenty** místo Material (zadání = 1:1 vzhled).
- **Přepínač vzhledu** (Světlý/Tmavý/Podle systému) jen na Androidu; ikona ⚙️ v hlavičce je nutná odchylka od iOS.
- **Opakování ve dnech** = nové pole `weekdays` (iOS ho ignoruje, JSON zůstává kompatibilní); ISO dny 1–7.
- **Přepínače funkcí globální** (`FeatureSettings` v SharedPreferences), ne per-připomínka. TTS nečte v tichém režimu ani bez CZ hlasu.
- **Přílohy = KOPÍROVAT do appky** (přežijí smazání originálu i přeinstalaci) – cena: zabírají místo, nutný úklid + limit (zatím nedodělaný).
- **Podpisový klíč `app/georeminder.keystore` (hesla `georeminder`) – NEZTRATIT** (jinak nepůjde instalovat přes stávající verzi). Při nové verzi **zvyšovat `versionCode`**.
- **Export/záloha dat:** v kódu zapojená (`SettingsSheet`), ale původní rozhodnutí znělo „mimo scope" – **ujasnit, jestli zůstává.**
- **minSdk 26, targetSdk 35.**

## 🔨 Build APK v cloud session (~10 min setup + ~1 min s cache)
1. SDK: cmdline-tools z `dl.google.com` → `sdkmanager --sdk_root=/opt/android-sdk "platform-tools" "platforms;android-35" "build-tools;35.0.0"`; `yes | sdkmanager --licenses`. Java 21.
2. **Gradle (past):** distribuce je jen na GitHub releases, ten je v cloudu zablokovaný → mirror `https://mirrors.aliyun.com/macports/distfiles/gradle/gradle-8.10.2-bin.zip`. AGP 8.7.3 chce Gradle 8.9+.
3. Zdroj: `git clone https://github.com/JendaNDT/GeoReminder-Android.git`. Doplnit tajnosti `app/georeminder.keystore` + `mapskey.properties` z Macu (mimo git). `local.properties`: `sdk.dir=/opt/android-sdk`.
4. `gradle :app:assembleRelease --no-daemon --console=plain`. Ověřit `aapt dump badging` + `apksigner verify`. Pro Play: `bundleRelease` → `.aab`.
- Verze: AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.09.03, Java 17.

## 📁 Kde co je
- **GitHub:** https://github.com/JendaNDT/GeoReminder-Android (`main`) – zdrojový kód + dokumentace. Tajnosti (mapový klíč, keystore) jsou mimo git (`.gitignore`), žijí jen v kopii na Macu.
- **Mac:** `/Users/jenda/Desktop/GeoReminder Android/` – `Android/GeoReminderAndroid/` = zdroják + docs; `Android/*.apk` = buildy; `AUDIT.md` + `AUDIT2.md` v kořeni.
- `NAVOD-INSTALACE.md` – jak APK dostat do telefonu; `GOOGLE-PLAY-CHECKLIST.md` – postup vydání.
