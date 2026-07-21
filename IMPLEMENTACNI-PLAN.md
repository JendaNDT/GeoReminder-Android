# Implementační plán oprav – GeoReminder Android

*Vychází z `AUDIT1.md` (21. 07. 2026, v1.6). Pokrývá všechny nálezy auditu ve 4 fázích podle priority. Týká se výhradně nativní Android verze (`Android/GeoReminderAndroid/`). Zahrnuje rozhodnutí z tvé zpětné vazby: cíl = Google Play, mazání s „Vrátit zpět", řízená záloha, přístupnostní průchod.*

---

## Jak plán používat

- **Fáze jsou seřazené podle priority.** Fázi 0 dělej první – tam jsou věci, kvůli kterým se dají ztratit data nebo se připomínka neozve. Zbytek je bezpečné dělat postupně.
- **Většina bodů jsou samostatné malé změny**, takže pořadí uvnitř fáze je flexibilní. Kde na sobě body závisí, je to napsané.
- **Osvědčený postup u tebe:** po každé dávce oprav nechat nezávislou revizi (druhý průchod kódem), teprve pak sestavit APK. Držme se toho.
- **Build/podpis:** vždy stejný keystore (`app/georeminder.keystore`, hesla `georeminder`) a **zvyšovat `versionCode`** (teď 8), ať jde APK nainstalovat přes stávající bez ztráty dat.
- **Značení náročnosti:** 🟢 malá (do ~1 dávky) · 🟡 střední · 🔴 větší zásah (rozmyslet zvlášť).
- **Návrh balení do verzí** je u každé fáze – sedí na tvůj způsob práce (v1.7, v1.8…). Není povinný.

Legenda u každého bodu: **Co & proč** → **Kde** → **Jak** → **Ověření** → náročnost.

---

## FÁZE 0 – Bezpečnost dat a doručení připomínek (KRITICKÉ) · návrh: v1.7

Tohle je jádro důvěry v appku. Bez toho může uživatel tiše přijít o připomínky nebo se mu neozvou. Dělat první.

### 0.1 Zpevnit načítání `reminders.json` – neztrácet data 🔴
- **Co & proč:** Dnes se celý seznam čte „všechno nebo nic". Jeden vadný/nekompatibilní záznam shodí načtení celku, appka ukáže „žádné připomínky" a **první nové uložení přepíše soubor** → ztráta všech připomínek. K tomu se přechodná chyba čtení tváří jako „prázdno" a vede taky k přepisu.
- **Kde:** `data/ReminderStore.kt:42–52` (`reload`), `data/ReminderStore.kt:105–111` (`persist`), `data/SharedStorage.kt:21–28` (`readText`), `model/Reminder.kt:105,161–163` (chybějící defaulty).
- **Jak:**
  1. `SharedStorage.readText` musí rozlišit tři stavy: *soubor neexistuje* (vrátí `null` = legitimní prázdno), *soubor existuje a přečetl se* (vrátí obsah), *čtení selhalo* (vyhodit/označit chybu, **ne** vrátit `null`). Např. vracet malý výsledný typ (`Ok(text) / Empty / Error`) nebo házet výjimku jen u skutečné chyby.
  2. `reload()`: při stavu *Error* **nepřepsat** stávající `_reminders` a **zablokovat následný `persist`** (dokud čtení neproběhne v pořádku), aby se platný soubor nepřepsal prázdným.
  3. Dekódovat **po záznamu**, ne celý list najednou: načíst JSON jako pole prvků, každý prvek zkusit `decodeFromString(Reminder.serializer())` v `try/catch`, vadný **přeskočit** (a spočítat, kolik se přeskočilo). Tím jeden špatný záznam nezahodí ostatní.
  4. Doplnit rozumné defaulty povinným polím v modelu (`Reminder.title = ""`, `FavoritePlace.name = ""`, `latitude/longitude = 0.0`), aby chybějící pole záznam nezabilo – kombinovat s bodem 3.
  5. Pojistka navíc: před přepisem souboru, když by nový seznam byl prázdný a starý soubor neprázdný, buď nezapisovat, nebo napřed udělat `.bak` kopii.
- **Ověření:** ručně vytvořit `reminders.json` s jedním rozbitým záznamem (např. `dueDate` jako text) mezi dvěma platnými → appka ukáže 2 platné, ne 0; přidání nové připomínky nesmí smazat ty dvě. Simulovat chybu čtení → appka nepřepíše soubor.
- Náročnost: 🔴 (nejdůležitější bod celého plánu)

### 0.2 Neztrácet časové připomínky (minulý čas + odložení přes reboot) 🟡
- **Co & proč:** (a) Jednorázová časová připomínka se zadaným časem v minulosti se **tiše nenaplánuje** a nic nevaruje. (b) **Odložená** (snooze) připomínka **nepřežije restart** telefonu – zmizí.
- **Kde:** `notify/ReminderScheduler.kt:248–251` (větev `NEVER`, `if (due <= now) return`), `:157–166` (`snooze`/`snoozeAt`), `:183–186` (`resync`), `notify/BootReceiver.kt:25–33`, `ui/EditReminderSheet.kt:775–783` (`canSave`).
- **Jak:**
  1. **Minulý čas při zadání:** ve formuláři nedovolit uložit jednorázový čas v minulosti – `canSave` rozšířit o kontrolu `dueDate > now` a u pole ukázat jemný hint („Vyber čas v budoucnu"). *(To je zároveň bod z validací.)*
  2. **Zmeškané přes reboot (catch-up):** v `BootReceiver`/`resync` u jednorázové časové připomínky, jejíž `dueDate` uplynul, když byl telefon vypnutý a ještě není `isDone`, notifikaci **doručit ihned** (dohnat), místo tichého zahození. Rozmysli hranici (např. jen pokud uplynulo < 24 h), ať to nespamuje starými.
  3. **Snooze přes reboot:** uložit stav odložení tak, aby ho `resync` obnovil. Nejjednodušší: při `snoozeAt` zapsat do prefs mapu `reminderId → snoozeAtMillis`; v `resync` pro tyto ID znovu nastavit `alarmPendingIntent(id, snooze = true)` na uložený čas (a po vyzvednutí/potvrzení záznam z mapy smazat).
- **Ověření:** (a) zkusit uložit připomínku na čas, který už byl → formulář to nepustí. (b) Odložit připomínku na +10 min, restartovat telefon → notifikace pořád přijde. (c) Nastavit jednorázovou na za 2 min, vypnout telefon, po 5 min zapnout → přijde dohnaná notifikace.
- Náročnost: 🟡

### 0.3 Ošetřit selhání registrace geofence + zpětná vazba 🟡
- **Co & proč:** `addGeofences` nemá ošetřené selhání – při systémovém limitu 100 geofence, vypnutých službách polohy nebo chybě proběhne **naprosto tiše**. Připomínka vypadá aktivní, ale systém ji nehlídá a uživatel to nepozná.
- **Kde:** `notify/ReminderScheduler.kt:220–226` (`addGeofence`), návaznost na bannery v `ui/ReminderListScreen.kt`.
- **Jak:**
  1. Na `geofencing.addGeofences(...)` doplnit `.addOnSuccessListener {}` a `.addOnFailureListener {}` – při selhání zalogovat a **dát uživateli signál** (např. banner „Hlídání místa se nepodařilo nastavit" nebo příznak u dané připomínky).
  2. Ohlídat limit: než registruješ další, spočítat aktivní geofence; při blížení ke 100 uživatele upozornit (osobní appka tolik míst mít nebude, ale ať to nespadne potichu).
  3. Zvážit rozlišení příčin (chybí „Povolit vždy" vs. vypnutá poloha vs. limit) a nabídnout odpovídající akci (do Nastavení / zapnout polohu).
- **Ověření:** vypnout služby polohy a vytvořit místní připomínku → objeví se srozumitelné upozornění, ne tichý neúspěch.
- Náročnost: 🟡

### 0.4 Souborové IO mimo hlavní vlákno + `catch` v receiverech 🟡
- **Co & proč:** Čtení i zápis `reminders.json` běží na hlavním (UI) vlákně – při delším seznamu jank / riziko ANR při každém swipe a při startu. A korutiny v receiverech nemají `catch` – neočekávaná výjimka by shodila proces.
- **Kde:** `data/ReminderStore.kt:42–52,105–111`; receivery `notify/GeofenceReceiver.kt:32–62`, `notify/AlarmReceiver.kt:23–40`, `notify/NotificationActionReceiver.kt:24–56`.
- **Jak:**
  1. Přesunout čtení/zápis souboru na `Dispatchers.IO` (v appce přes korutinu/`viewModelScope`/`Dispatchers.IO`; ve `ReminderStore` metody, které volá UI, nechat rychlé a IO odbavit mimo main). Pozor na pořadí: UI stav aktualizovat po dokončení zápisu.
  2. Do korutin v receiverech obalit tělo `try { … } catch (e: Exception) { Log } finally { pending.finish() }`.
- **Ověření:** appka je svižná i s ~100 připomínkami; žádné zaseknutí při swipe; StrictMode (dočasně zapnutý) nehlásí disk IO na main threadu.
- Náročnost: 🟡

### 0.5 Drobné pojistky logiky/stability (nízké riziko) 🟢
- **`nextWeekly` s neplatnými `weekdays`** (`notify/ReminderScheduler.kt:79–86`): po vyčerpání pojistky vrátit nejbližší platný cíl, ne den mimo výběr. 🟢
- **`markGeofenceFired` race** (`notify/ReminderScheduler.kt:142–146`, `notify/GeofenceReceiver.kt:54`): read-modify-write nad prefs Set → při dvou souběžných událostech se ID ztratí. Použít synchronizaci (např. `synchronized` blok kolem čtení+zápisu) nebo `commit()` s přečtením čerstvé hodnoty. 🟢
- **Request/notif kódy z `hashCode()`** (`notify/ReminderScheduler.kt:127,274`, `notify/NotificationHelper.kt:111–146`): teoretická kolize. Nízká priorita; pokud řešit, odvodit stabilní int ID z UUID jinak než `hashCode`. 🟢
- **Konečné selhání zápisu tiše spolknuto** (`data/SharedStorage.kt:31–41`): aspoň zalogovat, ať se ví, že zápis neproběhl. 🟢

---

## FÁZE 1 – UX a přístupnost (DŮLEŽITÉ) · návrh: v1.8

Appka funguje, ale pro některé uživatele je nepřístupná a v pár místech mate. Pro cíl Google Play (veřejní uživatelé) to patří do scope.

### 1.1 Přístupnostní průchod (TalkBack) 🟡
- **Co & proč:** Se čtečkou obrazovky dnes **nejde** připomínku odškrtnout ani smazat (swipe akce TalkBack neprovede a náhrada chybí) a **nejdou** ovládat vlastní přepínače/segmenty/volby (nemají sémantiku stavu). Opravy jsou levné – přidávají se „role" a popisky, appka se nepřepisuje.
- **Kde:** `ui/ReminderListScreen.kt:400–470` (swipe řádek), `ui/components/IOSComponents.kt:193–223,274–320` (IOSSwitch, SegmentedControl), `ui/SettingsSheet.kt:56–78`, `ui/EditReminderSheet.kt:449–474` (denní volby), plus klikací ikony bez popisu (`ui/LocationPickerSheet.kt:326–335`, `ui/ReminderListScreen.kt:492`).
- **Jak:**
  1. Ke swipe řádku doplnit **custom accessibility actions** (`Modifier.semantics { customActions = listOf(Hotovo, Smazat) }`), aby šly akce vyvolat i bez gesta.
  2. Vlastním přepínačům dát `Modifier.toggleable(value, role = Role.Switch)`, segmentům/volbám `Modifier.selectable(selected, role = Role.RadioButton)` – TalkBack pak oznámí zapnuto/vypnuto a vybranou volbu.
  3. Klikacím ikonám dodat `contentDescription` (křížek „Smazat hledání", ikona typu „Příjezd/Odjezd/Čas").
- **Ověření:** zapnout TalkBack (Nastavení → Přístupnost) a projít: založit připomínku, odškrtnout, smazat, přepnout druh upozornění – vše jde a je srozumitelně předčítané.
- Náročnost: 🟡

### 1.2 Mazání swipem – „Vrátit zpět" (snackbar) 🟢 — ROZHODNUTO
- **Co & proč:** Omylem provedený swipe teď nenávratně smaže připomínku. Rozhodl ses přidat androidí undo.
- **Kde:** `ui/ReminderListScreen.kt:415–418` (`onDelete`).
- **Jak:** po smazání ukázat `Snackbar` s akcí „Vrátit zpět" (cca 4–5 s). Implementačně: připomínku z UI odebrat hned, ale skutečné `store.delete` potvrdit až po vypršení snackbaru; při „Vrátit zpět" obnovit. Pozor obnovit i naplánované spouštěče (nebo mazat/rušit spouštěče až po vypršení).
- **Ověření:** smazat → objeví se snackbar → „Vrátit zpět" připomínku vrátí i s funkčním připomenutím; po vypršení je smazání trvalé.
- Náročnost: 🟢

### 1.3 Rozlišit „offline/chyba sítě" od „nic nenalezeno" 🟢
- **Co & proč:** Když hledání selže bez sítě, ukáže se „Nic jsem nenašel" – uživatel je uveden v omyl, že místo neexistuje.
- **Kde:** `ui/LocationPickerSheet.kt:395–409` (zobrazení), `:579–599` (logika hledání).
- **Jak:** odlišit výsledek „chyba/timeout spojení" od „0 výsledků" a ukázat jinou hlášku („Nejsi online / hledání teď nejde") případně s tlačítkem „Zkusit znovu".
- **Ověření:** zapnout letadlový režim, hledat → hláška o připojení, ne „nenalezeno".
- Náročnost: 🟢

### 1.4 Odezva na dotyk (ripple/haptika) 🟢
- **Co & proč:** `iosClickable` má globálně `indication = null`, takže ťuknutí nedává žádnou vizuální ani hmatovou odezvu – UI působí „mrtvě".
- **Kde:** `ui/components/IOSComponents.kt:48–54`.
- **Jak:** doplnit jemnou odezvu – buď decentní ripple/`indication`, nebo iOS-styl krátký fade/scale + lehká haptika (`HapticFeedback`) u hlavních akcí. Držet se decentní úrovně kvůli iOS vzhledu.
- **Ověření:** ťuknutí na tab/řádek/tlačítko dává znatelnou, ale nevtíravou odezvu.
- Náročnost: 🟢

### 1.5 Dotykové cíle ≥ 48 dp 🟢
- **Co & proč:** Několik cílů je pod doporučeným minimem, hůř se trefují.
- **Kde:** „+" nové oblíbené `ui/FavoritesSheet.kt:93` (38 dp), křížek hledání a denní kolečka `ui/LocationPickerSheet.kt:326–335` / `ui/EditReminderSheet.kt:451` (20/34 dp), „Přeskočit" v onboardingu.
- **Jak:** zvětšit klikatelnou plochu na ≥ 48 dp (vizuální prvek může zůstat menší, stačí zvětšit `Modifier` s paddingem / `minimumInteractiveComponentSize`).
- **Ověření:** prvky jdou pohodlně trefit palcem; případně zkontrolovat Layout Inspectorem.
- Náročnost: 🟢

---

## FÁZE 2 – Dokumentace a technický dluh (DŮLEŽITÉ/levné) · návrh: v1.9

Levné úklidové věci, které usnadní další práci a sníží riziko tichých chyb. Architektonické body (2.5) jsou volitelné a větší.

### 2.1 Aktualizovat `PROJECT_STATUS.md` 🟢
- **Co & proč:** Dokument je zdroj pravdy mezi sessions, ale je rozporný: ř. 72 popisuje `Geocoder` jako hlavní hledání vs. ř. 68 Photon; „Příští krok" (ř. 8–9) pořád u v1.5; ř. 62 „žádné známé bugy" už neplatí; ř. 81 vs. 24 (APK s/bez klíče).
- **Kde:** `PROJECT_STATUS.md`.
- **Jak:** sjednotit hledání (Photon primární, Geocoder záloha), posunout „Příští krok" na aktuální stav, doplnit odkaz na `AUDIT1.md` a známé nálezy, opravit poznámku o APK klíči.
- **Ověření:** dokument je vnitřně konzistentní a odpovídá kódu.
- Náročnost: 🟢

### 2.2 Sjednotit duplicity do sdílených míst 🟡
- **Slider poloměru** (`ui/EditReminderSheet.kt:334–336`, `ui/FavoritesSheet.kt:350–352`, `ui/LocationPickerSheet.kt:491–493`) → jedna komponenta `RadiusSlider` (rozsah 50–1000, krok 25). 🟢
- **Dekódování `reminders.json`** (`data/ReminderStore.kt:42–51` vs. `widget/GeoReminderWidget.kt:74–86`) → sdílená čtecí funkce (navazuje na 0.1). 🟢
- **Výchozí poloměr `150.0`** (`model/Reminder.kt:112,164`, `ui/EditReminderSheet.kt:104`, `ui/FavoritesSheet.kt:240`) → jedna konstanta. 🟢
- **Názvy dnů v týdnu** ve třech zdrojích (`model/CzechFormat.kt:27–28`, `ui/EditReminderSheet.kt:448`) → jeden zdroj. 🟢
- **Ověření:** změna hodnoty na jednom místě se projeví všude; nic se nerozjede.
- Náročnost: 🟡 (dohromady)

### 2.3 Magická čísla a stringy → konstanty 🟢
- **Prefs název `"georeminder"`** na 4 místech (`ui/RootScreen.kt:66`, `notify/ReminderScheduler.kt:29`, `ui/theme/ThemeController.kt:20`, `data/RecentPlaces.kt:13`) → jedna konstanta (překlep = tichá ztráta nastavení).
- **`EXTRA_REMINDER_ID`** definované 2× (`notify/ReminderScheduler.kt:35`, `notify/NotificationHelper.kt:35`) → jedna sdílená konstanta.
- **Snooze 60 min natvrdo** (`notify/NotificationActionReceiver.kt:36`) sjednotit s textem „o hodinu"; ostatní čísla (XOR masky, requestCode 1000, výchozí +1 h) pojmenovat.
- **Ověření:** grep neukáže duplicitní literály; chování beze změny.
- Náročnost: 🟢

### 2.4 Odstranit dead code 🟢
- Nepoužitý import `NotificationHelper` v `data/ReminderStore.kt:5`.
- Nevyužité oprávnění `ACCESS_NETWORK_STATE` v `AndroidManifest.xml:25` (pokud se opravdu nepoužívá).
- Duplicitní import `getValue` v `ui/LocationPickerSheet.kt:46,77`; plně kvalifikované `IOSSlider` na `:489` nahradit importem.
- `ui/ReminderListScreen.kt:99` `remember { FavoritesStore.get(context) }` – buď použít výsledek, nebo „zahřátí" udělat čitelněji.
- **Ověření:** projekt se přeloží bez varování o nepoužitých; APK bez zbytečného oprávnění.
- Náročnost: 🟢

### 2.5 (Volitelné, větší) Architektura 🔴
- **Globální event-bus přes statické `MutableStateFlow`** (`MainActivity.kt:16–20` + `ui/RootScreen.kt` + `ui/ReminderListScreen.kt`) a **množství singletonů** bez ViewModelu → zvážit `ViewModel` + předávání stavu, ať zmizí skrytá vazba a re-emit po rotaci.
- **`ReminderScheduler` instancovaný ad hoc na 4 místech** → udělat z něj singleton jako Store/Favorites.
- **God-soubory** `ui/EditReminderSheet.kt` (783 ř.) a `ui/LocationPickerSheet.kt` (750 ř.) → rozdělit (ukládání do mapperu, hledání do samostatné třídy, dialogy zvlášť).
- **Pozn.:** tohle nedělej, dokud ti to nebrání v práci – je to úklid, ne oprava chyby. Rozmyslet zvlášť.
- Náročnost: 🔴

---

## FÁZE 3 – Příprava na Google Play + UI polish · návrh: v2.0

Až bude appka spolehlivá a přístupná, tahle fáze ji dostane na Google Play a doladí drobnosti.

### 3.1 Příprava na vydání na Google Play 🟡🔴
- **Minifikace:** zapnout `isMinifyEnabled = true` (R8/ProGuard) v `app/build.gradle.kts:48` a otestovat, že nic nepadá (u appky bez reflexe většinou stačí výchozí pravidla).
- **Řízená záloha (`allowBackup`):** ponechat zapnutou, ale doplnit explicitní pravidla – `data_extraction_rules.xml` (Android 12+) a `full_backup_content` (starší) – a deklarovat ve formuláři **Data safety**. *(Tvé rozhodnutí: zapnuto, řízeně.)*
- **Poloha na pozadí (`ACCESS_BACKGROUND_LOCATION`):** geofence ji nutně potřebuje. Play ji schvaluje zvlášť – prominentní vysvětlení uživateli, zdůvodňovací formulář, často **krátké demo video** ukazující, proč ji appka potřebuje. Play navíc v dubnu 2026 pravidla polohy zpřísnil → ověřit aktuální požadavky.
- **Přesné budíky (`USE_EXACT_ALARM`):** Play povoluje jen appkám typu budík/kalendář/připomínky – GeoReminder do kategorie spadá, ale je nutné to **při odeslání zdůvodnit**. Alternativa: zůstat u `SCHEDULE_EXACT_ALARM`, kde si oprávnění zapíná uživatel sám.
- **Dále:** **privacy policy** (odkaz v Play i v appce), vyplněný **Data safety**, aktuální **target API**, ikona/store listing.
- **Pozn.:** tady doporučuju samostatný krok-za-krokem checklist (rád ho připravím) – je to spíš administrativa než kód.
- **Ověření:** interní testovací track na Play projde review; instalace z Play funguje.
- Náročnost: 🟡🔴 (kód malý, administrativa Play větší)

### 3.2 UI polish (kosmetika) 🟢
- **Neaktivní tab příliš tmavý** (`ui/RootScreen.kt:231`) → nevybraný tab dát šedou (secondary), ať je jasné, co je aktivní.
- **Hodnota poloměru „poskakuje"** (`ui/LocationPickerSheet.kt:496–502`) → použít tabulkové/monospace číslice.
- **Barva hvězdičky nekonzistentní** (`ui/EditReminderSheet.kt:719` vs. `ui/FavoritesSheet.kt:202`, `ui/LocationPickerSheet.kt:430`) → sjednotit (žlutá).
- **Datum vs. čas – dvě různě vypadající okna** (`ui/EditReminderSheet.kt:640–687`) → sjednotit vzhled (obě jako vlastní karta, nebo obě Material).
- **Onboarding bez scrollu** (`ui/OnboardingScreen.kt:87–115`) → obal do `verticalScroll`, ať se na malém displeji/velkém písmu nic neořízne.
- **Kontrast druhotného textu** – u 11–12sp popisků je pod WCAG AA (iOS kompromis); zvážit mírné ztmavení jen u nejmenších.
- Náročnost: 🟢

### 3.3 Ověřit na zařízení (ne kód, ale test) 🟢
- **Horní inset v celoobrazovkovém dialogu** (`ui/EditReminderSheet.kt:596–616`, `ui/LocationPickerSheet.kt:256–259`) – hlavička spoléhá na `statusBarsPadding()`; ověřit, že se „Zrušit"/„Vybrat místo" na Androidu 15/Samsung neschová pod status bar (u spodní lišty tenhle problém byl). Když ano, dotáhnout přes `ActivityInsets` i pro horní inset.
- **Ořez při zvětšeném systémovém písmu** (150–200 %) – projít formulář, segmenty, taby.
- **Onboarding v landscape** na malém displeji.
- Náročnost: 🟢 (testovací)

---

## Rychlý přehled priorit

| Fáze | Co | Proč teď / později |
|---|---|---|
| **0 – v1.7** | Ukládání dat, doručení připomínek, geofence, IO mimo main | Bez toho hrozí ztráta dat / neozve se → **první** |
| **1 – v1.8** | Přístupnost (TalkBack), undo mazání, offline hláška, odezva dotyku, velikost cílů | Pro veřejné vydání (Play) a použitelnost |
| **2 – v1.9** | Dokumentace, duplicity, magická čísla, dead code (+ volitelně architektura) | Levný úklid, snižuje riziko tichých chyb |
| **3 – v2.0** | Google Play (minify, záloha, oprávnění, privacy, Data safety) + UI polish + testy na zařízení | Až je appka spolehlivá a přístupná |

**Doporučené pořadí prvních kroků:** 0.1 (ztráta dat) → 0.2 (časové připomínky) → 0.3 (geofence) → 0.4 (IO/receivery) → pak Fáze 1.

*Až budeš chtít, můžu vzít kterýkoli bod (nebo celou dávku) a rovnou ho naimplementovat – stačí říct číslo. Po každé dávce udělám nezávislou revizi, než sestavíme APK.*
