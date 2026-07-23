# GeoReminder Android — finální komplexní audit a stav oprav (AUDIT4)

*Audit výchozího stavu v2.7 / versionCode 19 a následná sada oprav, provedené 23. 7. 2026 nad projektem `Android/GeoReminderAndroid/`.*

## Krátké shrnutí

Po následné opravné iteraci už v kódu nezůstává žádný známý kritický blocker z tohoto auditu: receivery čekají na cold-start načtení, zápisy jsou durabilní a chybové stavy viditelné, přílohy i zálohy jsou omezené na bezpečné cesty a rozepsané formuláře nelze omylem zahodit. CZ/EN lokalizace, základní TalkBack sémantika, kontrast, targetSdk 36, signing, Gradle Wrapper, CI a release gate jsou dotažené; 19/19 unit testů, oba linty, debug APK i minifikovaný release AAB procházejí. Emulator Android 16 potvrdil Skip onboarding bez permission dialogu, kompletní změnu jazyka, dirty-state dialog, durabilní uložení a načtení po force-stop. Aplikace je nyní vhodná k poctivému terénnímu testu, nikoli ještě automaticky k vydání: chybí reálný dlouhodobý test alarmů/geofence/rebootu na fyzických telefonech a instrumentační process-death suite; architektonické dělení a hi-fi Glass/mapa/widget zůstávají jako odložitelný polish.

## Stav po opravách

Původní nálezy níže zůstávají zachované jako auditní stopa a popisují stav **před opravami**; čísla řádků se po změnách přirozeně posunula. Aktuální stav je:

- **Opraveno:** L1–L4, L6, A1, A3, A4, A6–A7, S1–S7, C1, C3–C5, U1–U3, U6–U7, V1, V4–V5 a související kosmetické dead-code/lokalizační nálezy.
- **Výrazně zlepšeno, ale ne absolutně uzavřeno:** L5 (validace, deduplikace, hromadné zápisy a best-effort rollback; dva samostatné JSON soubory ale netvoří jednu filesystem transakci), A2/A5/C2 (cyklus `model → data` a mrtvý kód jsou pryč, velké composables a část sdílených utilit zůstávají), S8 (19 unit testů a CI, zatím bez receiver/Compose instrumentace), U4/U5 (loading/error, Photon offline stav a recurrence přes `Instances` opraveny; produktová semantika kalendářní adresy zůstává otevřená).
- **Vědomě odloženo:** V2–V3 — plný frosted-glass efekt a hi-fi redesign mapy, kalendáře a widgetu. Funkční mapě přibylo alespoň tlačítko návratu na vlastní polohu.
- **Ověření po opravách:** `testDebugUnitTest`, `lintDebug`, `lintRelease`, `assembleDebug`, `bundleRelease`; 19 testů, 0 selhání, 0 lint chyb. Zbylá lint varování jsou pouze dostupné aktualizace závislostí/nástrojů, nikoli nalezená funkční chyba.

## Jak audit proběhl

- Přečteny byly `AGENTS.md`, oba `PROJECT_STATUS.md`, `README.md`, předchozí audity, release checklist, design handoff a design tokeny.
- Zkontrolováno bylo 49 produkčních Kotlin souborů, manifest, Gradle konfigurace, resources, testy a vazby mezi `model`, `data`, `notify`, `ui` a `widget`.
- Tři paralelní kontroly se samostatně zaměřily na logiku/stabilitu, architekturu/čistotu a UX/UI; nejzávažnější nálezy byly znovu ověřeny v hlavním auditu.
- Přes lokální Gradle 8.10.2 prošlo `testDebugUnitTest lintDebug assembleDebug` i `testReleaseUnitTest lintRelease bundleRelease`. Výsledek: 10/10 testů, 0 lint chyb, ale 101 varování.
- Na emulátoru Android 16 (API 36, 1080 × 2400) prošel smoke test onboarding → permission dialogy → hlavní obrazovka → nastavení → nový reminder bez pádu GeoReminderu. Test přímo potvrdil smíšenou češtinu/angličtinu po volbě English.
- Dynamické chování alarmu/geofence po cold startu nebylo end-to-end automatizováno; kritický závod je však přímo doložen kontraktem metod a shodně jej nezávisle našly dvě kontroly.

---

## 1. Vnitřní logika

### Silné stránky

- `ReminderStore` a `FavoritesStore` poskytují UI jeden reaktivní stav přes `StateFlow` (`data/ReminderStore.kt:35-36`, `data/FavoritesStore.kt:20-21`).
- Zápis je serializovaný, používá `AtomicFile` a při fyzické chybě čtení existuje ochrana `loadFailed` (`data/SharedStorage.kt:68-104`, `data/ReminderStore.kt:30-33`).
- Formulář blokuje prázdný název, chybějící lokaci a jednorázový čas v minulosti (`ui/EditReminderSheet.kt:171-176`).
- Plánovač rozlišuje jednorázové, denní a týdenní reminder-y a bez oprávnění k přesnému alarmu degraduje na `setAndAllowWhileIdle` (`notify/ReminderScheduler.kt:342-377`).
- Kopie přílohy má reálný limit 10 MB, kontrolu i během streamování a maže nedokončený soubor (`data/AttachmentHelper.kt:14-80`).

### Kritické nálezy

#### [KRITICKÁ] L1 — systémové události čtou data dřív, než se načtou

- **Soubory:** `data/ReminderStore.kt:35-39,55-79`; `notify/AlarmReceiver.kt:26-29`; `notify/GeofenceReceiver.kt:35-40`; `notify/BootReceiver.kt:28-30`; `notify/NotificationActionReceiver.kt:27-30`; `ui/RootScreen.kt:140-149`.
- `reload()` není suspendující a pouze spustí coroutine. Všechny receivery hned na dalším řádku čtou `reminders.value` nebo volají `resyncAll()`.
- V nově nastartovaném procesu je `StateFlow` inicializovaný prázdným seznamem. Alarm nebo geofence tedy může reminder nenajít a jednorázovou systémovou událost ztratit; boot nic neobnoví a tlačítko Hotovo/Odložit může být no-op.
- Jde o přímý problém hlavní funkce aplikace a rozpor s tvrzením o „spolehlivém doručení & obnově“ v `PROJECT_STATUS.md:13`.

#### [KRITICKÁ] L2 — importovaná cesta přílohy může smazat jiná data aplikace

- **Soubory:** `data/BackupManager.kt:52-66`; `data/AttachmentHelper.kt:83-91`; `data/ReminderStore.kt:94-95,115-119`.
- Import zachová libovolnou absolutní `attachmentPath`. Mazání pak volá `File(path).delete()` bez ověření, že kanonická cesta leží v `files/attachments/`.
- Upravená JSON záloha může ukázat například na `reminders.json` nebo `favorites.json`; běžné smazání či úprava importovaného reminderu pak smaže jiné uživatelské soubory. Import je uživatelský vstup, takže cesta musí být sanitizovaná stejně jako souřadnice.

### Důležité nálezy

#### [DŮLEŽITÁ] L3 — zcela poškozený JSON se vydává za legitimní prázdný seznam

- **Soubory:** `data/SharedStorage.kt:29-50`; `data/ReminderStore.kt:58-74`; `data/AttachmentHelper.kt:94-105`.
- Pokud nelze parsovat ani kořenové JSON pole, `decodeReminders()` vrátí `emptyList()` místo chyby. Store proto nastaví `loadFailed = false`, zobrazí prázdno, povolí další přepsání a spustí úklid všech příloh jako „osiřelých“.
- Atomický zápis chrání proti půlce zápisu, ne proti poškození, nekompatibilnímu formátu nebo ručně obnovenému souboru. Následná běžná změna může dokončit ztrátu všech dat.

#### [DŮLEŽITÁ] L4 — selhání zápisu je pro volajícího neviditelné

- **Soubory:** `data/SharedStorage.kt:90-103`; `data/ReminderStore.kt:139-155`; `notify/NotificationActionReceiver.kt:24-58`.
- `writeText()` chybu pouze zaloguje a vrací `Unit`; paměť, scheduler i UI se tváří, že změna uspěla. Po ukončení procesu se změna vrátí zpět.
- Receiver navíc ukončí `PendingResult` dřív, než oddělený `ioScope` skutečně zapíše stav Hotovo. Android může studený proces ukončit mezi těmito kroky.

#### [DŮLEŽITÁ] L5 — import není transakční ani plně validovaný/deduplikovaný

- **Soubory:** `data/BackupManager.kt:52-81`; `notify/ReminderScheduler.kt:81-106`.
- Import ověřuje souřadnice a omezuje poloměr, ale nekontroluje prázdný název, povinný `dueDate`, dny 1–7, `NaN` poloměr ani duplicity ID uvnitř samotné zálohy. Snapshot `currentReminders` se během smyčky neaktualizuje, takže dva nové záznamy se stejným ID se oba přidají a sdílejí alarm/notifikaci.
- Import se aplikuje po položkách; chyba uprostřed nechá částečně obnovená data. Export navíc vrátí `true`, i když `openOutputStream()` vrátí `null` (`data/BackupManager.kt:36-39`).

#### [DŮLEŽITÁ] L6 — textová záloha zachovává nefunkční absolutní cesty k přílohám

- **Soubory:** `data/BackupManager.kt:25-43`; `model/Reminder.kt:139-140`; `res/values/strings.xml:136`.
- Produkt přiznává, že soubory příloh neexportuje, ale reminder přesto exportuje absolutní cestu ze starého zařízení. Po obnově je odkaz neplatný a může kolidovat s cestami na novém zařízení.
- Pokud má záloha zůstat textová, musí export/import cestu vynulovat. Pokud má obnovovat přílohy, potřebuje balíček souborů a nové mapování cest.

### Kosmetické nálezy

- `nextWeekly()` přijme neplatné dny mimo 1–7 a po bezpečnostní smyčce vrátí termín, který neodpovídá vstupu (`notify/ReminderScheduler.kt:81-106`).
- Několik nízkoúrovňových operací polyká výjimky bez logu či zpětné vazby (`data/AttachmentHelper.kt:83-106,109-133`, `data/CalendarImporter.kt:55-57`, `data/LocationHolder.kt:69-71`).

---

## 2. Provázanost funkcí a architektura

### Silné stránky

- Pro velikost aplikace je základní rozdělení `model/`, `data/`, `notify/`, `ui/`, `widget/` rychle pochopitelné.
- Manifestové receivery pro alarm, geofence a notifikační akce nejsou exportované (`app/src/main/AndroidManifest.xml:92-105`).
- Reminder stav je centralizovaný a seznamová filtrace, hledání a Undo jsou alespoň částečně vytažené do `ReminderListViewModel` (`ui/viewmodel/ReminderListViewModel.kt:20-84`).
- Sdílené komponenty, témata a tokeny omezují část vizuální duplicity.

### Kritické nálezy

#### [KRITICKÁ] A1 — kontrakt store ↔ receivery není awaitovatelný

- **Soubory:** stejné jako L1; primární detail viz **L1**.
- Nejde jen o lokální race condition. Veřejné API `reload()` svým názvem naznačuje dokončené načtení, ale fakticky jen něco naplánuje. Čtyři nezávislé komponenty proto stejným způsobem používají chybný kontrakt.
- Oprava má být architektonická: suspendující `reloadAndGet()`, atomické `loadAndFind(id)` / `loadAndResync()`, případně repository s explicitním stavem `Loading/Ready/Error`.

### Důležité nálezy

#### [DŮLEŽITÁ] A2 — vrstvy tvoří skutečné cykly a store má příliš mnoho odpovědností

- **Soubory:** `model/CzechFormat.kt:3`; `data/ReminderStore.kt:6-8,22-28`; `notify/AlarmReceiver.kt:7`; `widget/GeoReminderWidget.kt:41-44`.
- Vznikají cykly `model ↔ data`, `data ↔ notify` a `data ↔ widget`. `ReminderStore` současně drží stav, serializuje, maže přílohy, plánuje systémové události a obnovuje widget.
- To ztěžuje izolované testy a je jedním z důvodů, proč asynchronní kontrakt receiverů unikl.

#### [DŮLEŽITÁ] A3 — lokalizační refaktor zůstal napůl a aktivní je stará kopie

- **Soubory:** aktivní `ui/ReminderListScreen.kt:528-693`; nepoužitá lokalizovaná kopie `ui/components/ReminderListComponents.kt:59-171`.
- Obrazovka volá vlastní český `ReminderActionSheet`; `QuickActionSheet`, který používá resources, má usage pouze ve své deklaraci.
- Stejný vzorec se opakuje u celého `ui/components/FormComponents.kt:42-162`: tři připravené komponenty se nikde nepoužívají a formulář si logiku implementuje znovu.

#### [DŮLEŽITÁ] A4 — rozpracované kategorie „visí ve vzduchu“

- **Soubory:** `model/LocationCategory.kt:9`; `model/Reminder.kt:141-142`.
- `LocationCategory` se používá jen ve vlastní deklaraci a `categoryId` jen jako serializované pole. Neexistuje store, UI ani scheduler logika.
- Buď je potřeba dokončit funkční tok, nebo pole odstranit/verzovat, aby JSON kontrakt nesliboval nepřítomnou funkci.

#### [DŮLEŽITÁ] A5 — obrazovky jsou skrytě propojené přes utility umístěné v jiných obrazovkách

- **Soubory:** `ui/FavoritesSheet.kt:336` používá `FormTextField` z `ui/EditReminderSheet.kt:933`; `ui/MapOverviewScreen.kt:94-119` používá `zoomForSpan/boundsAround` z `ui/LocationPickerSheet.kt:580-595`.
- Odstranění nebo větší refaktor jedné obrazovky překvapivě rozbije jinou. Sdílené utility patří do samostatných komponent/doménových souborů.

#### [DŮLEŽITÁ] A6 — copy-paste stores se už funkčně rozešel

- **Soubory:** `data/ReminderStore.kt:58-64`; `data/FavoritesStore.kt:42-53`; `PROJECT_STATUS.md:12`.
- Reminder-y mají pokus o dekódování po záznamech, Favorites dekóduje celý list naráz a jediný vadný záznam zablokuje všechna oblíbená místa. Dokumentace přitom tvrdí totéž odolné chování pro oba stores.

#### [DŮLEŽITÁ] A7 — dokumentace popisuje několik nezapojených funkcí

- **Soubory:** `README.md:14` vs. `notify/NotificationHelper.kt:171-174` — v notifikaci chybí deklarovaná akce Navigovat.
- `PROJECT_STATUS.md:14` tvrdí uvolňování TTS, ale `notify/TtsSpeaker.kt:73-80` nemá žádného volajícího.
- `PROJECT_STATUS.md:2-4` tvrdí plnou CZ/EN lokalizaci, ale aktivní UI obsahuje desítky českých literálů; detail viz U2.
- `PROJECT_STATUS.md:9` uvádí 2.7/code 19, zatímco `PROJECT_STATUS.md:34` stále 2.5/code 17.

### Kosmetické nálezy

- Nepoužitý `ReminderScheduler.get()` existuje vedle aktivního vytváření nových instancí (`notify/ReminderScheduler.kt:47-53`; `data/ReminderStore.kt:24`).
- `res/raw/map_style_dark.json` je nepoužitý a duplikuje mapový styl vložený v `ui/theme/MapStyles.kt:10`; potvrzuje to Lint.
- Kontrakty jako `"shortcut_kind"`, `"location"`, `"reminders.json"` a rozsah 50–1000 m jsou opakované jako magické hodnoty v několika vrstvách.

---

## 3. Stabilita

### Silné stránky

- Všechny čtyři receivery používají `goAsync()` a `finish()` ve `finally`, takže neblokují `onReceive()` (`notify/AlarmReceiver.kt:23-62`, obdobně ostatní receivery).
- Singletony drží `applicationContext`, ne Activity, takže zde není klasický leak obrazovky (`data/ReminderStore.kt:22-24`, `notify/TtsSpeaker.kt:18-21`).
- AlarmManager má fallback bez exact-alarm oprávnění (`notify/ReminderScheduler.kt:367-377`).
- Geofence registrace hlásí asynchronní failure do UI místo úplně tichého selhání (`notify/ReminderScheduler.kt:310-322`).

### Kritické nálezy

#### [KRITICKÁ] S1 — alarm/geofence/boot/notification action se v cold procesu mohou ztratit

- **Soubory:** viz **L1/A1**.
- Stabilitní dopad je nejhorší u AlarmReceiveru: systém jednorázový `PendingIntent` doručí, receiver reminder kvůli prázdnému stavu nenajde a událost se už sama neopakuje. U opakovaného alarmu se navíc nenaplánuje další výskyt.

#### [KRITICKÁ] S2 — úklid příloh závodí s právě vybraným, ještě neuloženým souborem

- **Soubory:** `ui/EditReminderSheet.kt:154-161,178-250`; `ui/RootScreen.kt:140-149`; `data/ReminderStore.kt:60-64`; `data/AttachmentHelper.kt:94-105`.
- Picker zkopíruje soubor do `attachments/`, ale reminder jej začne referencovat až po Uložit. Návrat z pickeru vyvolá `ON_RESUME → reload()`, jehož cleanup porovnává adresář jen s posledním uloženým seznamem a nový soubor může smazat.
- Při nahrazení přílohy existujícího reminderu pak update může smazat i původní soubor a uložit cestu na už neexistující nový soubor.

### Důležité nálezy

#### [DŮLEŽITÁ] S3 — evidence „už vystřeleno“ není atomická s doručením

- **Soubory:** `notify/ReminderScheduler.kt:182-203,347-356`; `notify/AlarmReceiver.kt:33-55`.
- Čtení a zápis setu jsou jednotlivě zamčené, ale sekvence kontrola → notifikace → zápis ne. Alarm a současný catch-up/resync mohou oba projít kontrolou a oba doručit notifikaci/TTS.

#### [DŮLEŽITÁ] S4 — úspěch jedné geofence může skrýt selhání jiné

- **Soubory:** `notify/ReminderScheduler.kt:242-250,310-317`.
- Všechny registrace sdílejí jeden Boolean. Každý success jej nastaví na `false`, každý failure na `true`; poslední callback vyhraje. Při limitu 100 geofence může pozdější úspěch schovat dřívější nehlídaný reminder.

#### [DŮLEŽITÁ] S5 — jednorázový reminder se označí za doručený i při zakázaných notifikacích

- **Soubory:** `notify/NotificationHelper.kt:189-205`; `notify/AlarmReceiver.kt:38-55`; `notify/GeofenceReceiver.kt:50-57`.
- `show()` potlačí `SecurityException` a nevrací výsledek. Alarm přesto nastaví fired flag a geofence se odregistruje. Po pozdějším povolení notifikací už reminder catch-up nedoručí.

#### [DŮLEŽITÁ] S6 — několik pomalých I/O cest běží přímo z UI callbacku

- **Soubory:** příloha `ui/EditReminderSheet.kt:154-161`; export/import `ui/SettingsSheet.kt:78-100`; calendar query `ui/CalendarImportSheet.kt:72-84`; implementace `data/BackupManager.kt:25-85`, `data/CalendarImporter.kt:18-59`.
- Cloudová příloha do 10 MB, velká záloha nebo pomalý Calendar provider mohou na Main threadu na sekundy zmrazit Compose a v krajním případě vyvolat ANR.

#### [DŮLEŽITÁ] S7 — TTS se vytváří i vypnuté a nikdy se neuvolní

- **Soubory:** `notify/TtsSpeaker.kt:18-41,73-80`.
- `speakIfEnabled()` volá `init()` ještě před kontrolou přepínače. `shutdown()` nemá usage. Neuniká Activity context, ale systémový TTS engine zůstává navázaný po celý život procesu i uživatelům, kteří TTS nepoužívají.

#### [DŮLEŽITÁ] S8 — klíčové stabilitní cesty nemají testy

- **Soubory:** pouze `app/src/test/.../ReminderTest.kt`, `CzechFormatTest.kt`, `PlaceLinkResolverTest.kt`; `app/src/androidTest/` chybí.
- Bez testu jsou store, scheduler, čtyři receivery, zálohy, přílohy, ViewModel i Compose toky. Zelených 10 testů proto neříká nic o skutečném doručení reminderu po zabití procesu/rebootu.

### Kosmetické nálezy

- `hashCode()` se používá jako notification/PendingIntent request code (`notify/NotificationHelper.kt:115-150`, `notify/ReminderScheduler.kt:146-154,380-390`). Kolize je s běžným počtem reminderů málo pravděpodobná, ale může přepsat cizí akci.
- Kotlin kompilace hlásí experimentální `limitedParallelism`, deprecated locale API, deprecated Wear extender a jednu deprecated ikonu. Nejde o současný pád, ale o migrační dluh.

---

## 4. Čistota a přehlednost kódu

### Silné stránky

- Názvy balíčků i většiny tříd jsou srozumitelné a produkční kód neobsahuje `TODO`, `FIXME` ani `HACK`.
- Doménový model je malý a JSON kompatibilita je čitelně dokumentovaná (`model/Reminder.kt:17-35,104-142`).
- Existují sdílené iOS-look komponenty, centrální `GeoColors` a čtyři řezy fontu Inter.
- Repo je čisté a tajné soubory Maps key/keystore jsou ignorované přes `.gitignore:10-15`.

### Kritické nálezy

V této dimenzi není samostatný kritický nález; kritické architektonické dopady jsou popsány jako L1/A1 a S2.

### Důležité nálezy

#### [DŮLEŽITÁ] C1 — repozitář nemá Gradle Wrapper

- **Soubory:** kořen `Android/GeoReminderAndroid/` — chybí `gradlew`, `gradlew.bat` i `gradle/wrapper/*`; build postup je v `README.md:23-31`.
- Standardní čistý checkout není reprodukovatelně sestavitelný deklarovanou verzí Gradlu; `android describe` končí `gradlew not found`. Audit sestavil projekt jen díky lokální ignorované `.gradle_dist/gradle-8.10.2`.

#### [DŮLEŽITÁ] C2 — velké obrazovky míchají stav, validaci, I/O a rendering

- **Soubory:** `ui/EditReminderSheet.kt` 987 řádků, `ui/LocationPickerSheet.kt` 723, `ui/ReminderListScreen.kt` 694, `ui/SettingsSheet.kt` 530.
- Velikost sama není chyba; problém je koncentrace launcherů, doménové validace, síťových/provider operací a celé UI struktury v jednom composable. To zhoršuje testovatelnost i bezpečné dokončení lokalizace.

#### [DŮLEŽITÁ] C3 — testovací a lint gate je příliš měkký

- **Soubory:** `app/build.gradle.kts:76-78`; testy v `app/src/test/`.
- Release lint je vypnutý a `abortOnError = false`; 101 varování proto build nikdy nezastaví. 72 z 125 českých string resources Lint označuje jako nepoužité, což přímo signalizuje nedokončené zapojení lokalizace.

#### [DŮLEŽITÁ] C4 — produkční signing heslo je v kódu a stejný klíč podepisuje debug

- **Soubory:** `app/build.gradle.kts:37-42,51-62`.
- Keystore je správně mimo Git, ale jeho heslo je veřejné a slabé; při úniku souboru je okamžitě použitelné. Debug build navíc používá produkční klíč, čímž zbytečně rozšiřuje plochu, kde se release identita používá.

#### [DŮLEŽITÁ] C5 — Play/publish rizika jsou vidět už v Lintu

- **Soubory:** `app/src/main/AndroidManifest.xml:13-21`; `ui/ReminderListScreen.kt:346`; `ui/SettingsSheet.kt:412`; `data/LanguageController.kt:26-28`; `app/build.gradle.kts:21-79`.
- Lint varuje před přímou žádostí `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, dynamickou změnou locale bez konfigurace language splitů v App Bundle a starším targetSdk 35. Před Play releasem je nutné ověřit aktuální policy/target požadavek a jazykové doručování z AAB; zelený bundle build není schválení politikou Play.

### Kosmetické nálezy

- `FormComponents.kt`, `QuickActionSheet`, `LocationCategory`, `categoryId`, `ReminderScheduler.get()` a externí `map_style_dark.json` jsou mrtvé nebo nedokončené artefakty; viz A3/A4 a kosmetické nálezy A.
- Styl není zcela konzistentní: vedle wildcard importů jsou duplicitní konkrétní importy (`ui/ReminderListScreen.kt:49-121`) a řada doménových/UI konstant je nepojmenovaná.
- Dokumentace a UI nesou tři verze současně: build 2.7/code 19 (`app/build.gradle.kts:28-29`), status dole 2.5/code 17 (`PROJECT_STATUS.md:34`) a Nastavení ukazuje v2.5 (`ui/SettingsSheet.kt:478-486`).

---

## 5. UX

### Silné stránky

- Hlavní tok je krátký: `+` → název → místo/čas → Uložit. Oblíbená místa, app shortcuts a sdílení z Map jej dále zrychlují.
- Hlavní seznam má prázdný stav, stav bez výsledků, Undo po smazání a vlastní TalkBack swipe akce (`ui/ReminderListScreen.kt:210-224,373-445`; `ui/components/ReminderListComponents.kt:302-309`).
- Formulář jasně blokuje minulý jednorázový čas a picker místa má loading i prázdný výsledek (`ui/EditReminderSheet.kt:171-176,331-337`; `ui/LocationPickerSheet.kt:415-432`).
- Permission bannery srozumitelně vysvětlují, co nebude fungovat. Emulator potvrdil dobrou čitelnost a velké akční plochy bannerů.
- Potvrzení mazání oblíbeného místa a Undo reminderu skutečně existují; tyto staré problémy z AUDIT3 jsou opravené (`ui/FavoritesSheet.kt:168-190`).

### Kritické nálezy

#### [KRITICKÁ] U1 — swipe/scrim obejde ochranu rozpracovaných formulářů

- **Soubory:** vnitřní ochrana `ui/EditReminderSheet.kt:273-314`, ale rodič `ui/ReminderListScreen.kt:457-464`; mapa `ui/MapOverviewScreen.kt:203-213`; Favorites `ui/FavoritesSheet.kt:138-155` vs. `273-286`.
- `BackHandler` a tlačítko Zrušit správně otevřou discard dialog, ale `ModalBottomSheet.onDismissRequest` rovnou vynuluje stav. Stačí sheet stáhnout dolů nebo klepnout na scrim a všechny rozepsané změny zmizí bez potvrzení.
- U reminderu se tím obchází i explicitní úklid nově vybrané přílohy. Jde o reálnou ztrátu uživatelské práce.

### Důležité nálezy

#### [DŮLEŽITÁ] U2 — volba English vytváří směs češtiny a angličtiny

- **Soubory:** `model/Reminder.kt:45-101,145-159`; `ui/ReminderListScreen.kt:241-426,579-693`; `ui/EditReminderSheet.kt:323-733`; `ui/SettingsSheet.kt:109-484`; `ui/LocationPickerSheet.kt:255-529`; `ui/theme/ThemeController.kt:11-18`.
- Připravené `values-en/strings.xml` má stejných 125 klíčů jako česká verze, ale většina aktivního UI používá české literály nebo české enum labely.
- Emulator: po klepnutí na English se v Nastavení přeložily jen tři položky volby jazyka; titul, vzhled, TTS, zálohy, hlavní obrazovka i nový reminder zůstaly česky. Hlavní obrazovka navíc `appLanguage` nepozoruje, takže se bez restartu ani nerekomponuje.
- Lint současně varuje, že runtime locale změny nejsou propojené s App Bundle language splits (`data/LanguageController.kt:26-28`).

#### [DŮLEŽITÁ] U3 — „Přeskočit“ onboarding ve skutečnosti začne žádat oprávnění

- **Soubory:** `ui/OnboardingScreen.kt:154-171`; `ui/RootScreen.kt:188-194`.
- Skip volá stejný `onFinish()` jako poslední tlačítko „Povolit a začít“, takže ihned otevře notifikační a lokační systémový dialog. Emulator tento sled potvrdil. Název akce neodpovídá výsledku a zhoršuje důvěru při prvním spuštění.

#### [DŮLEŽITÁ] U4 — stavy načítání a chyb jsou u několika toků neúplné

- **Soubory:** Calendar `ui/CalendarImportSheet.kt:68-85,147-159`; sdílený link `ui/ReminderListScreen.kt:197-206`; Photon `data/PhotonLocationRepository.kt:41-89` a `ui/LocationPickerSheet.kt:603-625`.
- Kalendář před dokončením synchronního dotazu nemá `isLoading` a může ukázat „Žádné události“. Rozpoznávání sdíleného Maps odkazu neukazuje žádný průběh. Photon interně mění síťovou chybu na prázdný seznam, takže vnější stav Offline je prakticky nedosažitelný a výpadek vypadá jako „nic nenalezeno“.

#### [DŮLEŽITÁ] U5 — kalendářový import má funkční i textové mezery

- **Soubory:** `data/CalendarImporter.kt:18-70`; `ui/CalendarImportSheet.kt:87-100,119-159`.
- Dotaz používá `CalendarContract.Events.DTSTART`, ne `CalendarContract.Instances`; běžná opakovaná série založená dříve proto může vynechat výskyt v příštích 30 dnech.
- Událost s adresou se vždy převede na časový reminder a adresa zůstane jen textem, přestože design handoff počítá s rozlišením místo/čas. Při žádosti READ_CALENDAR se navíc zobrazuje text o notifikačním oprávnění (`CalendarImportSheet.kt:130`).

#### [DŮLEŽITÁ] U6 — TalkBack a dotykové terče nejsou dotažené

- **Soubory:** nepojmenovaný vlastní switch `ui/components/IOSComponents.kt:227-274` a použití např. `ui/SettingsSheet.kt:215-235`; tabs `ui/RootScreen.kt:262-291`; malé cíle `ui/ReminderListScreen.kt:302-310`, `ui/EditReminderSheet.kt:767-788`, `ui/LocationPickerSheet.kt:339-355`, `widget/GeoReminderWidget.kt:173-184`.
- Switch má roli a stav, ale ne label řádku; čtečka typicky oznámí jen „switch on/off“. Spodní tabs nemají `Role.Tab` ani `selected`. Několik ikon má aktivní plochu 18–40 dp místo doporučených 48 dp.

#### [DŮLEŽITÁ] U7 — mapa nemá návrat na vlastní polohu

- **Soubory:** `ui/MapOverviewScreen.kt:146-171`.
- Systémové my-location tlačítko je vypnuté a vlastní tlačítko z handoffu chybí. Po odtažení mapy se uživatel nemůže jedním krokem vrátit k sobě.

### Kosmetické nálezy

- Sekce Aktivní/Hotové neukazují počty požadované handoffem (`ui/ReminderListScreen.kt:404-426`; `ui/components/IOSComponents.kt:190-199`).
- Nastavení ukazuje v2.5 místo 2.7 (`ui/SettingsSheet.kt:478-486`).
- Widget má české „Vše vyřízeno“, český subtitle a popis plusu i v English režimu (`widget/GeoReminderWidget.kt:112-127,151-165,178-184`).

---

## 6. UI

### Silné stránky

- Light/Dark/Neutral/Glass mají centrální palety a typografie používá přibalený Inter ve čtyřech řezech (`ui/theme/Color.kt`, `ui/theme/Type.kt`).
- Typové dlaždice a řádky dobře sledují handoff: 36 × 36 dp, radius 11 dp, min. řádek 62 dp a oddělovač odsazený pod text (`ui/components/ReminderListComponents.kt:329-413`).
- Edge-to-edge je zapnuté a hlavní tab bar respektuje navigation inset (`MainActivity.kt:23-31`; `ui/RootScreen.kt:218-225`). Mapový picker má promyšlený fallback spodního insetu pro dialogová okna.
- Oprava kontrastu permission banneru z AUDIT3 je skutečná: tmavý text `#1C1C1E` na oranžové je dobře čitelný (`ui/components/PermissionBanner.kt:45-70`) a emulator to potvrdil.
- Smoke test ukázal celkově čistý, konzistentní a na běžném telefonu příjemný vizuál bez zjevných překryvů obsahu.

### Kritické nálezy

V této dimenzi nebyl nalezen samostatný vizuální blocker; UI rizika jsou důležitá, ale nevedou sama o sobě k pádu nebo ztrátě dat.

### Důležité nálezy

#### [DŮLEŽITÁ] V1 — barevné čipy a některá primární tlačítka nesplňují kontrast malého textu

- **Soubory:** palety `ui/theme/Color.kt:38-80`; čip `ui/components/ReminderListComponents.kt:395-410`; primary button `ui/components/IOSComponents.kt:448-467`.
- Oranžový 12sp text na 12% oranžovém podkladu vychází přibližně 2:1, teal zhruba 2,6:1; požadavek pro malý text je 4,5:1. Bílý 17sp text na light/dark modrém akcentu vychází přibližně 4,0:1 / 3,7:1.
- Nejde o problém permission banneru, ale o opakované malé čipy a CTA v ostatních obrazovkách.

#### [DŮLEŽITÁ] V2 — Glass není implementovaný jako předaný frosted-glass směr

- **Soubory:** `ui/theme/Color.kt:122-149`; `ui/components/IOSComponents.kt:201-214`; `ui/RootScreen.kt:166-185`.
- Karty jsou jen průhledná barva bez blur/fallbacku; chybí radiální vrstvy. Titulky používají tmavou barvu přímo nad pohyblivým gradientem, a proto se kontrast mění podle podkladu.
- Pokud je design handoff závazný 1:1, jde o významnou mezeru. Pokud je jen inspirace, lze ji přesunout do polish backlogu.

#### [DŮLEŽITÁ] V3 — mapa, kalendář a widget jsou proti hi-fi referenci výrazně zjednodušené

- **Soubory:** mapa `ui/MapOverviewScreen.kt:146-180`; kalendář `ui/CalendarImportSheet.kt:160-209`; widget `widget/GeoReminderWidget.kt:99-188`.
- Mapa používá standardní markery bez barevných typových pinů, calloutu a počtu míst. Kalendář je jedna prostá karta bez denních skupin a typových štítků. Widget používá malé jednotně modré ikony místo barevných dlaždic a nemá hlavičku „NEJBLIŽŠÍ“.

#### [DŮLEŽITÁ] V4 — hlavička sheetu není robustní pro dlouhé texty a font scaling

- **Soubory:** `ui/components/IOSComponents.kt:143-186`; dlouhé kalendářové tlačítko `ui/CalendarImportSheet.kt:107-116`.
- Titul má pevný padding 72 dp z obou stran, boční pilulky nemají limit. Na užším telefonu nebo s větším systémovým písmem se anglické texty mohou překrýt nebo oříznout.

#### [DŮLEŽITÁ] V5 — titul pickeru místa nemá spolehlivý kontrast nad mapou

- **Soubory:** `ui/LocationPickerSheet.kt:267-290`.
- Text leží přímo nad mapovými dlaždicemi bez scrimu či stabilního podkladu. Čitelnost proto závisí na konkrétním místě mapy.

### Kosmetické nálezy

- Sdílené tokeny nejsou pixelově 1:1 s handoffem: karta 26 místo 22 dp a bez předepsaného stínu (`ui/components/IOSComponents.kt:201-214`), tab item 96 × 56 místo 92 × 52 (`ui/RootScreen.kt:271-285`), reminder title 17sp Regular místo 16sp Medium (`ui/components/ReminderListComponents.kt:379-386`).
- Emulator ukázal, že horní pilulky modal sheetů vizuálně zasahují do plochy status baru; text zůstává čitelný, ale při dalších velikostech obrazovky je vhodné ověřit `statusBarsPadding` (`ui/components/IOSComponents.kt:154-167`).
- `LightGeoColors` a `NeutralGeoColors` záměrně používají vyšší opacity sekundárních textů než design tokeny. Kontrast je lepší, ale vizuální hierarchie méně jemná (`ui/theme/Color.kt:43-45,99-101`).

---

## Doporučené další kroky

### 1. Nejdřív — terénní ověření

1. Na fyzickém telefonu otestovat jednorázový i opakovaný časový reminder po zavření aplikace, force-stop scénář odděleně, reboot, změnu času a několikahodinový Doze.
2. Otestovat příjezd/odjezd do geofence s oprávněním „Vždy“, vypnutými/zapnutými notifikacemi a následnou obnovou po restartu. Ideálně alespoň čistý Android/Pixel a jeden agresivnější Samsung/Xiaomi.
3. Ověřit naléhavý kanál, dožadování, Hotovo a oba snooze toky na zařízení s reálným zvukem a zamčenou obrazovkou.

### 2. Před veřejným releasem

4. Přidat instrumentační testy receiverů a Compose journey test alespoň pro create/edit/discard; unit testy již kryjí storage decode, scheduler a resolver, ale Android process death nelze věrohodně simulovat čistým JVM testem.
5. Projít `GOOGLE-PLAY-CHECKLIST.md`, doplnit produkční signing secrets mimo Git, rozhodnout o zvýšení `versionCode/versionName` a ověřit finální podepsaný AAB přes Play internal testing.
6. Změřit UI s font scale 1,3–1,5 a na malém i velkém displeji. Hlavička už se nepřekrývá, ale kompletní responsivní matice nebyla automatizována.

### 3. Lze odložit

- Rozdělení velkých composables/store odpovědností a přesun zbývajících sdílených utilit do samostatných souborů.
- Plný frosted-glass/blur efekt, vlastní mapové piny/callouty a hi-fi redesign kalendáře či widgetu.
- Hromadné aktualizace všech knihoven pouze kvůli dostupnosti novější verze; současná kombinace je sestavitelná a lint upozornění nejsou funkční chyby.

## Otevřené otázky pro Jendu

1. **Je design handoff závazný 1:1, nebo určuje jen směr?** To rozhoduje, zda V2/V3 patří před release, nebo do budoucího polish backlogu.
2. **Má kalendářní událost s adresou vytvořit reminder na místo, nebo vždy na čas?** Implementace ji nyní poctivě importuje jako časový reminder a adresu ponechá jako popis.
3. **Má „8:00 denně“ zůstat 8:00 místního času po změně časové zóny?** Scheduler počítá další výskyt v aktuální zóně, ale speciální produktový test změny zóny zatím chybí.
4. **Má budoucí záloha přenášet i fyzické přílohy?** Bezpečný současný formát je záměrně vynechává a při exportu/importu nuloval absolutní cesty.
5. **Jak široká má být podporovaná device matrix?** OEM battery management je u lokační reminder aplikace zásadnější než další kosmetický polish.

## Celkový verdikt

**GeoReminder je po opravách důvěryhodná release candidate pro terénní/internal test.** Původní kritické mezery v cold-process doručení, zápisech, přílohách a dirty-state UX jsou odstraněné a automatický debug/release gate je zelený. Za hotový veřejný release jej má smysl prohlásit až po fyzickém testu alarmů, geofence, rebootu a battery managementu; to je nyní hlavní zbytkové riziko, nikoli známý kritický defekt v kódu.
