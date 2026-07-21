# AUDIT1 – Komplexní audit GeoReminder Android

*Vypracováno 21. 07. 2026. Statická analýza kódu verze v1.6 (versionCode 8), Kotlin + Jetpack Compose, minSdk 26 / target 35. Auditováno bez sestavení a bez běhu na zařízení – nálezy jsou čtené přímo ze zdrojového kódu, u každého je uvedena cesta a řádek. Připomínky vázané na místo (geofence) i čas (přesné budíky).*

---

## Shrnutí (co je dobré, co je riziko, kam dál)

GeoReminder je na vibecoding projekt **nadprůměrně čistá a promyšlená aplikace**: přehledná struktura balíčků, věrný designový systém (barvy, typografie, tmavý režim 1:1 dle iOS předlohy), správně řešená oprávnění, atomický zápis souborů a disciplinovaná souběžnost (žádné `!!`, žádný `GlobalScope`, správné flagy u notifikací). Žádná funkce „nevisí ve vzduchu" – vše deklarované je i zapojené.

Hlavní **riziko je koncentrované do jednoho místa – do vrstvy ukládání a doručování připomínek**. Existuje reálný scénář **tiché ztráty všech připomínek** (jeden vadný/nekompatibilní záznam v JSON shodí načtení celého seznamu a další uložení soubor přepíše) a několik situací, kdy se **časová připomínka nikdy neozve** (termín v minulosti, odložení přes noc + restart telefonu). K tomu přibývá **tiché selhání registrace geofence** bez jakékoli zpětné vazby uživateli. Tyto věci nejsou vidět na první pohled, ale v provozu bolí nejvíc, protože podkopávají důvěru v to, že se připomínka opravdu spustí.

Druhá vrstva rizik je „měkčí": **přístupnost** (appka je pro uživatele s TalkBackem u klíčových akcí prakticky neovladatelná), pár **UX mezer** (offline se tváří jako „nic nenalezeno", mazání bez „Vrátit zpět", chybějící odezva na dotyk) a **technický dluh** (globální singletony místo ViewModelu, drobné duplicity, zastaralá/rozporná `PROJECT_STATUS.md`).

**Kam dál:** nejdřív zpevnit ukládání a doručení připomínek (sekce 1 a 3), pak doladit přístupnost a UX zpětnou vazbu (sekce 5). Architekturu a kosmetiku řešit až potom – nehoří.

---

## 1. Vnitřní logika (datové toky, stavy, edge case, validace, error handling)

### Silné stránky
- **Atomický a serializovaný zápis dat.** Ukládání jde přes `SharedStorage.writeText`, které píše do dočasného souboru a pak přejmenuje (`data/SharedStorage.kt:31–41`); všechny mutace v `ReminderStore` jsou `@Synchronized` (`data/ReminderStore.kt:54–88`). V rámci procesu se dvě uložení nemůžou navzájem přepsat.
- **Defenzivní parsování drobných vstupů.** Hledání, nedávná místa a rozbor odkazů z Map mají try/catch, kontroly `isNaN()`, `toDoubleOrNull()` (`data/PlaceLinkResolver.kt:59–72`, `data/RecentPlaces.kt`), formátování dnů má `coerceIn` (`model/CzechFormat.kt:37–39`).
- **Rozumné fallbacky.** Přesný budík má zálohu na nepřesný, když chybí oprávnění (`notify/ReminderScheduler.kt:259–270`); hledání má Geocoder jako zálohu Photonu.

### Kritické
- **`data/ReminderStore.kt:42–52` + `model/Reminder.kt:105,161–163` — jeden vadný záznam zahodí CELÝ seznam a další uložení ho trvale přepíše.**
  Načítání je „všechno, nebo nic": pokud kterýkoli záznam v `reminders.json` má hodnotu špatného typu nebo chybějící povinné pole (`Reminder.title` a `FavoritePlace.name/latitude/longitude` nemají výchozí hodnotu), dekódování celého seznamu spadne, `reload()` tiše skončí (`catch { return }`) a v paměti zůstane prázdný seznam. UI ukáže „žádné připomínky". Jakmile uživatel přidá jednu novou, `persist()` (`:105–111`) přepíše soubor obsahem `[nová]` → **všechny původní připomínky jsou nenávratně pryč**. Riziko je o to reálnější, že cílem je JSON kompatibilní s iOS – stačí, aby druhá platforma nebo ruční úprava zapsala jeden neočekávaný typ. **Doporučení:** dekódovat záznam po záznamu a vadný přeskočit (ne shodit celek); před přepisem souboru mít pojistku (např. neukládat prázdný seznam přes neprázdný soubor bez potvrzení).

- **`data/SharedStorage.kt:21–28` + `data/ReminderStore.kt:43` — přechodná chyba čtení se tváří jako „prázdno" a vede k přepisu.**
  `readText` polyká všechny výjimky a vrací `null`; nerozlišuje „soubor neexistuje" od „čtení selhalo". Když přechodná IO chyba nastane při studeném startu, appka naběhne bez připomínek a **první uložení přepíše platný soubor** prázdným seznamem. **Doporučení:** odlišit neexistenci od chyby a při chybě čtení blokovat zápis.

### Důležité
- **`notify/ReminderScheduler.kt:248–251` — jednorázová časová připomínka v minulosti se tiše nikdy neozve.**
  Větev `NEVER` dělá `if (due <= now) return` – budík se vůbec nenastaví a uživatele nic nevaruje. Formulář přitom čas v minulosti nezakazuje (`ui/EditReminderSheet.kt:775–783`, `canSave` neřeší budoucnost). Praktický dopad: v 10:00 vytvořím připomínku na „dnes 8:00" → uloží se, ale nepřipomene. Stejně tak po restartu telefonu se minulý jednorázový termín zahodí (žádné „opožděné" doručení).

- **`notify/ReminderScheduler.kt:163–166,183–186` + `notify/BootReceiver.kt:25–33` — odložená (snooze) připomínka nepřežije restart telefonu.**
  Odložení plánuje budík přes samostatný PendingIntent (`snooze = true`). Restart všechny budíky zruší, ale `resync` obnovuje jen `schedule(it)` s `snooze = false` – odloženou variantu neobnoví. U jednorázového termínu je navíc původní `dueDate` už v minulosti → zahozeno (viz předchozí bod). Výsledek: **odložím připomínku na zítra ráno, telefon přes noc rebootne, připomínka se ztratí.**

- **`ui/EditReminderSheet.kt:775–783` — chybí validace budoucnosti u času a (spolu s výše uvedeným) tichý neúspěch.**
  `canSave` kontroluje jen vyplněnost, ne že je čas v budoucnu. Ve spojení s tím, že minulý čas se nenaplánuje, vzniká „uložím, ale nic se nestane". **Doporučení:** buď nedovolit uložit minulý čas, nebo uživatele upozornit.

### Kosmetické
- **`notify/ReminderScheduler.kt:79–86` — `nextWeekly` s neplatnými `weekdays` (jen z poškozeného/importovaného JSON) po 15 iteracích vrátí den mimo výběr.** UI to sice neumožní zadat, jen pojistka pro cizí data.

---

## 2. Provázanost funkcí (komunikace modulů, závislosti, duplicity, „ve vzduchu")

### Silné stránky
- **Žádná mrtvá obrazovka ani „visící" feature.** Všechny 4 receivery i widget receiver mají implementaci a jsou zapojené; nenašel se kód, který se nikdy nezavolá jako celek.
- **Mapová vrstva je izolovaná** do dvou souborů (`ui/LocationPickerSheet.kt`, `ui/MapOverviewScreen.kt`) – reálně vyměnitelná, jak tvrdí dokumentace.
- **Formátování a popisky jsou centralizované** (`model/CzechFormat.kt`, `enum.label`) – žádná roztroušená duplicita textů.

### Důležité
- **`MainActivity.kt:16–20` + `ui/RootScreen.kt:163,169` + `ui/ReminderListScreen.kt:143–157` — skrytý globální „event bus" přes statické `MutableStateFlow`.**
  `shortcutRequest` a `sharedPlaceText` jsou statické flow kolektované ve dvou composables zároveň. Funguje to, ale je to skrytá vazba na globální stav a `StateFlow` drží poslední hodnotu (riziko re-emitu po otočení displeje). Spolu s absencí ViewModelu je tohle hlavní architektonický dluh.

- **Obousměrná závislost `data` ↔ `notify` + nekonzistentní životní cyklus scheduleru.**
  `ReminderStore` drží `ReminderScheduler` (`data/ReminderStore.kt:19`), zatímco receivery v `notify/` volají zpět `ReminderStore.get()`. `ReminderScheduler` se přitom vytváří ad hoc na 4 místech (`notify/GeofenceReceiver.kt:54`, `notify/AlarmReceiver.kt:35`, `notify/NotificationHelper.kt`, plus ten ve Store) – na rozdíl od Store/Favorites, které jsou singletony. Nekonzistentní vzor „jednou singleton, jindy `new`".

- **God-soubory: `ui/EditReminderSheet.kt` (783 ř.) a `ui/LocationPickerSheet.kt` (750 ř.).**
  Jeden composable řeší formulář, mapování ukládání pro 4 kombinace, kalendář, time-picker i mapový dialog; druhý míchá UI, `photonSearch`, `geocoderSearch`, reverzní geokódování i geometrii. Těžko se v tom orientuje a testuje. Vhodné rozdělit (ukládání do mapperu, hledání do samostatné třídy, dialogy zvlášť).

### Kosmetické
- **Duplicitní dekódování `reminders.json`** – vlastní kopie v `data/ReminderStore.kt:42–51` i `widget/GeoReminderWidget.kt:74–86`. Šlo by sdílet.
- **Trojnásobná duplicita slideru poloměru** – `ui/EditReminderSheet.kt:334–336`, `ui/FavoritesSheet.kt:350–352`, `ui/LocationPickerSheet.kt:491–493` (pokaždé `50f..1000f`, `steps = 37`, zaokrouhlení po 25 m). Patří do jedné komponenty.
- **Dvě definice `EXTRA_REMINDER_ID = "reminder_id"`** (`notify/ReminderScheduler.kt:35`, `notify/NotificationHelper.kt:35`) – dnes konzistentní, ale změna jedné hodnoty tiše rozbije doručování.
- **Mrtvý import** `NotificationHelper` v `data/ReminderStore.kt:5` (nepoužit). **Nevyužité oprávnění** `ACCESS_NETWORK_STATE` (`AndroidManifest.xml:25`).
- **Dvě JSON knihovny pro totéž** – `org.json` (hledání, nedávná místa) vedle `kotlinx.serialization` (model). Sjednocení by zjednodušilo kód.

---

## 3. Stabilita (race conditions, async, paměť, pády, determinismus)

### Silné stránky
- **Čisté async vzory.** Nikde `!!`, `GlobalScope`, `runBlocking`; receivery používají `goAsync()` + `finally { pending.finish() }`.
- **Správné PendingIntent flagy.** Budíky/notifikace mají `FLAG_IMMUTABLE`, geofence správně `FLAG_MUTABLE` (`notify/ReminderScheduler.kt:131,229–241,281`).
- **Nagging (dožadování) je pojištěné.** Před dalším budíkem se kontroluje, zda jsou notifikace zapnuté (`notify/NotificationHelper.kt:182–189`) – nevzniká neviditelná nekonečná smyčka; budíky přežijí smrt procesu a opakované budíky nemají kumulativní drift (přepočet z hodiny/minuty pokaždé).

### Kritické
- **`notify/ReminderScheduler.kt:220–226` — registrace geofence bez ošetření selhání a bez limitu 100.**
  `geofencing.addGeofences(...)` nemá `addOnFailureListener` ani `addOnSuccessListener`. Selhání (systémový limit 100 geofence, vypnuté služby polohy, `GEOFENCE_NOT_AVAILABLE`) proběhne **naprosto tiše** – připomínka v seznamu vypadá aktivní, ale systém ji nehlídá. Chybí i jakákoli kontrola počtu aktivních geofence. Uživatel nemá jak zjistit, že se hlídání nezaregistrovalo. *(Přesahuje i do UX – žádná zpětná vazba.)*

### Důležité
- **`data/ReminderStore.kt:42–52,105–111` — veškeré čtení/zápis úložiště běží na hlavním vlákně.**
  `reload()` (čtení + JSON dekódování) i `persist()` (kódování + zápis) se volají přímo z UI (start appky, každý swipe/přepnutí v seznamu). Pro pár desítek připomínek neznatelné, ale s rostoucím seznamem to znamená jank / riziko ANR při každé interakci a při studeném startu. IO patří na `Dispatchers.IO`.

- **`notify/GeofenceReceiver.kt:54` + `notify/ReminderScheduler.kt:142–146` — race při zápisu značky „vystřeleno".**
  `markGeofenceFired` dělá čtení-úprava-zápis nad množinou v SharedPreferences s `apply()`. Dvě geofence události doručené jako oddělené `onReceive` běží každá ve vlastní korutině, obě přečtou původní množinu a zapíšou → poslední vyhraje a jedno ID se ztratí. Ztracená jednorázová geofence se pak může odpálit podruhé. Nízká pravděpodobnost, ale reálné (`apply` → `commit` a synchronizace by to řešily).

- **`notify/GeofenceReceiver.kt:32–62`, `notify/AlarmReceiver.kt:23–40`, `notify/NotificationActionReceiver.kt:24–56` — korutina v receiveru bez záchytného `catch`.**
  Bloky mají `try … finally`, ale ne `catch`. Kdyby něco uvnitř hodilo neočekávanou runtime výjimku, propadne z korutiny a shodí proces. Doporučeníhodné obalit catch-all a jen zalogovat.

### Kosmetické
- **`notify/NotificationHelper.kt:111–146` + `notify/ReminderScheduler.kt:127,274` — request/notif kódy z `String.hashCode()` mohou teoreticky kolidovat.** U UUID a hrstky připomínek zanedbatelné.
- **`data/SharedStorage.kt:31–41` — konečné selhání zápisu (i fallbacku) je tiše spolknuto** – změna zůstane jen v paměti, při dalším `reload` se ztratí. Nízká pravděpodobnost.

---

## 4. Čistota a přehlednost kódu (struktura, pojmenování, dead code, magická čísla, dokumentace)

### Silné stránky
- **Přehledná struktura** `model / data / notify / ui / widget` s malými, soudržnými soubory a stručnou KDoc hlavičkou u většiny tříd.
- **Nulové `TODO/FIXME/HACK/XXX`** v kódu – žádné rozdělané polotovary.
- **Konzervativní, konzistentní závislosti** (Compose BOM 2024.09.03, Kotlin 2.0.21, maps-compose 6.1.2), nic vyloženě zastaralého; žádná nepoužitá dependency.

### Kritické (pro orientaci v projektu)
- **`PROJECT_STATUS.md:8–9,68,72 — zastaralá a vnitřně rozporná dokumentace.**
  „Příští krok" (ř. 8–9) pořád píše *„Otestovat v1.5"*, přestože dokument je u v1.6. Zásadnější: řádek 72 popisuje **`Geocoder` jako hlavní způsob hledání** („Hledá přednostně do ~30 km…"), což si přímo protiřečí s řádkem 68 (**Photon primární, geokodér jen záloha**) i s realitou kódu. Protože tenhle soubor slouží jako hlavní můstek mezi sessions (i pro Claude v příští session), je zavádějící vodítko past. *(Menší: ř. 62 „Žádné známé bugy" už po tomto auditu neplatí; ř. 81 tvrdí „APK zatím bez mapového klíče", zatímco ř. 24 říká, že klíč je zapečen.)*

### Důležité
- **Magický string `"georeminder"` pro SharedPreferences na 4 místech** (`ui/RootScreen.kt:66`, `notify/ReminderScheduler.kt:29`, `ui/theme/ThemeController.kt:20`, `data/RecentPlaces.kt:13`). Bez společné konstanty; překlep = jiný soubor nastavení = tichá ztráta dat/voleb.
- **Roztroušená magická čísla** – výchozí poloměr `150.0` na 4 místech (`model/Reminder.kt:112,164`, `ui/EditReminderSheet.kt:104`, `ui/FavoritesSheet.kt:240`); XOR masky request codes (`0x0F0F0F`, `0x5A5A5A`), geofence requestCode `1000`, snooze `60` minut natvrdo (`notify/NotificationActionReceiver.kt:36`) proti textu „o hodinu".

### Kosmetické
- **Trojí zdroj názvů dnů v týdnu** – `model/CzechFormat.kt:27–28` a `ui/EditReminderSheet.kt:448`.
- **Duplicitní import** `getValue` v `ui/LocationPickerSheet.kt:46,77`; jednou volané `IOSSlider` plně kvalifikovaně (`:489`) místo importu – nekonzistence stylu.
- **`ui/ReminderListScreen.kt:99`** `remember { FavoritesStore.get(context) }` jen „zahřívá" singleton a zahazuje výsledek – čte se to jako omyl.
- **Release build bez R8/ProGuard** (`app/build.gradle.kts:48` `isMinifyEnabled = false`) – pro osobní použití OK, pro Google Play by se hodilo zvážit.

---

## 5. UX (uživatelské toky, stavy, zpětná vazba, přístupnost)

### Silné stránky
- **Robustní řetěz oprávnění** v pořadí notifikace → poloha → poloha „Vždy" (`ui/RootScreen.kt:84–127`), nad seznamem čtyři kontextové bannery přehodnocované při návratu do popředí (`ui/ReminderListScreen.kt:116–139,221–268`).
- **Prázdné i mezistavy jsou skutečně řešené** – sdílený `EmptyState` (`ui/components/IOSComponents.kt:322`), u hledání loading spinner, hláška „nic nenalezeno" i návrhy oblíbených/nedávných.
- **Chytrý výběr místa** – debounce hledání, Photon + záloha Geocoder, reverzní geokódování po ťuknutí, živý náhled kruhu poloměru.

### Kritické
- **`ui/ReminderListScreen.kt:400–470` — swipe akce Hotovo/Vrátit/Smazat jsou pro TalkBack nedostupné.**
  `ReminderRow` nemá žádnou náhradní přístupnostní akci; uživatel se čtečkou umí připomínku jen otevřít, ale nedokáže ji označit jako hotovou ani smazat. U klíčové operace je to slepá ulička.
- **`ui/components/IOSComponents.kt:193–223,274–320` (+ `ui/SettingsSheet.kt:56–78`, `ui/EditReminderSheet.kt:449–474`) — vlastní přepínače/segmenty/volby nemají sémantiku stavu.**
  `IOSSwitch`, `SegmentedControl` a řádky voleb jsou `Box` + klik bez `toggleable(role = Switch)` / `selectable(role = RadioButton)`. TalkBack je nepřečte jako přepínač/volbu a neoznámí zapnuto/vypnuto. Formulář připomínky je tak pro nevidomého prakticky neovladatelný. *(M3 `Slider` sémantiku má, ten je OK.)*

### Důležité
- **`ui/components/IOSComponents.kt:48–54` — globálně vypnutá odezva dotyku (žádný ripple ani haptika).**
  `iosClickable` má `indication = null` a používá se všude. Ťuknutí nedává vizuální ani hmatovou odezvu → UI působí „mrtvě"/nejistě.
- **`ui/LocationPickerSheet.kt:395–409` — výpadek sítě se tváří jako „nic nenalezeno".**
  Když Photon i Geocoder selžou bez sítě, ukáže se „Nic jsem nenašel". Uživatel offline je uveden v omyl, že místo neexistuje. Chybí odlišení chyby sítě od prázdného výsledku.
- **`ui/ReminderListScreen.kt:415–418` — smazání swipem bez potvrzení a bez „Vrátit zpět".**
  Omylem provedený swipe nenávratně smaže připomínku; na Androidu uživatelé čekají snackbar s undo. (Odpovídá iOS chování – viz Otevřené otázky.)
- **Dotykové cíle pod 48 dp** – „+" pro oblíbené `38.dp` (`ui/FavoritesSheet.kt:93`), křížek hledání `20.dp` a denní kolečka `34.dp` (`ui/LocationPickerSheet.kt:326–335`, `ui/EditReminderSheet.kt:451`), „Přeskočit" jen 13sp text. Hůř se trefují.
- **Interaktivní ikony bez `contentDescription`** – např. křížek hledání (`ui/LocationPickerSheet.kt:326–335`) a ikona typu v řádku (`ui/ReminderListScreen.kt:492`) – TalkBack je neoznámí.

### Kosmetické
- **Riziko ořezu textu při zvětšeném systémovém písmu** – komponenty s pevnou výškou (`SegmentedControl` 36 dp, popisky tabů v `caption2`) vs. text v sp (`ui/components/IOSComponents.kt:285`, `ui/RootScreen.kt:250`). *(K ověření na zařízení při 150–200 % písma.)*

---

## 6. UI (vizuální konzistence, vrstvy, responsivita, polish)

### Silné stránky
- **Věrný designový systém** – kompletní světlá i tmavá paleta (`ui/theme/Color.kt`), celá škála iOS stylů mapovaná na Inter (`ui/theme/Type.kt`), font zadrátovaný i do Material Typography kvůli systémovým dialogům (`ui/theme/Theme.kt:25–41`).
- **Důsledný tmavý režim** – nejen theme barvy, ale i `map_style_dark.json` pro Google mapu a barva ikon status baru podle **zvoleného** vzhledu, ne jen systému (`ui/theme/Theme.kt:64–71`).
- **Vynucený český formát data** (`model/CzechFormat.kt`) opravuje iOS vadu s „at"; `data/ActivityInsets.kt` řeší schované tlačítko na mapě na Samsungu.

### Důležité
- **`ui/EditReminderSheet.kt:596–616` + `ui/LocationPickerSheet.kt:256–259` — horní inset v celoobrazovkovém dialogu k ověření.**
  Spodní lišta je řešená `ActivityInsets` workaroundem, ale horní hlavička spoléhá na `statusBarsPadding()` uvnitř téhož dialogu. Pokud okno nedostává ani horní insety (stejný problém jako u spodních na Androidu 15/Samsung), „Zrušit"/„Vybrat místo" by se schovalo pod status bar. **Doporučuji ověřit na reálném zařízení.**

### Kosmetické
- **`ui/RootScreen.kt:231` — neaktivní tab je příliš tmavý** (`colors.label` místo šedé secondary) → hůř se pozná, který tab je aktivní.
- **`ui/LocationPickerSheet.kt:496–502` — hodnota poloměru není v tabulkových číslicích** → při změně 50→1000 m mírně „poskakuje".
- **Nekonzistentní barva hvězdičky** – čip v `ui/EditReminderSheet.kt:719` má hvězdu v barvě textu, seznam oblíbených (`ui/FavoritesSheet.kt:202`) a návrhy (`ui/LocationPickerSheet.kt:430`) žlutou.
- **Vizuální nesoulad výběru data vs. času** – datum je Material `DatePickerDialog`, čas je vlastní karta se zaoblením 26 (`ui/EditReminderSheet.kt:640–687`) – dvě různě vypadající okna vedle sebe.
- **Onboarding bez scrollu** (`ui/OnboardingScreen.kt:87–115`) – na landscape/malém displeji nebo při velkém písmu se obsah může oříznout. *(K ověření.)*
- **Kontrast druhotného textu** – `secondaryLabel` 60 % a placeholdery 30 % jsou iOS-standard, ale u 11–12sp popisků pod WCAG AA (vědomý kompromis kvůli iOS paritě).

---

## Doporučené další kroky

**Řešit nejdřív (bezpečnost dat a spolehlivost doručení – sekce 1 a 3):**
1. **Zpevnit načítání `reminders.json`**: dekódovat po záznamu a vadný přeskočit místo shození celého seznamu; odlišit „soubor neexistuje" od „čtení selhalo" a při chybě čtení **nedovolit přepis** souboru. *(Nejvyšší priorita – jediné místo, kde reálně hrozí ztráta všech dat.)*
2. **Neztrácet časové připomínky**: upozornit / nedovolit uložení času v minulosti; obnovit i odložené (snooze) budíky po restartu telefonu.
3. **Ošetřit selhání registrace geofence** (`addOnFailureListener`) a dát uživateli zpětnou vazbu, když se hlídání místa nepodařilo zaregistrovat; ohlídat limit 100 geofence.
4. **Přesunout souborové IO mimo hlavní vlákno** (`Dispatchers.IO`) a doplnit `catch` do korutin v receiverech.

**Řešit potom (UX a přístupnost – sekce 5):**
5. Doplnit přístupnostní sémantiku (custom accessibility akce u swipe řádků, `role = Switch/RadioButton` u vlastních komponent, `contentDescription` u klikacích ikon).
6. Odlišit „offline/chyba sítě" od „nic nenalezeno"; přidat snackbar s „Vrátit zpět" u mazání; vrátit odezvu na dotyk (ripple/haptika).
7. Zvětšit dotykové cíle pod 48 dp.

**Až po opravách správnosti (dluh, kosmetika, příprava na vydání – sekce 2, 4, 6):**
8. Aktualizovat a sjednotit `PROJECT_STATUS.md` (odstranit rozpor Photon vs. Geocoder, posunout „Příští krok" na v1.6).
9. Levné sjednocení duplicit: komponenta `RadiusSlider`, konstanta názvu prefs, konstanta výchozího poloměru, sdílené dekódování JSON.
10. **Příprava na Google Play (potvrzený cíl):** zapnout R8/ProGuard (`isMinifyEnabled = true`) a řízené zálohování (viz rozhodnutí 3 níže). Počítej s tvrdšími bránami Play u téhle appky: **poloha na pozadí** (`ACCESS_BACKGROUND_LOCATION`) – geofence ji nutně potřebuje (spouští se při zavřené appce), a Play ji schvaluje zvlášť: prominentní vysvětlení uživateli, zdůvodňovací formulář a často krátké demo video; Play navíc v dubnu 2026 pravidla polohy zpřísnil, takže ověř aktuální požadavky. **Přesné budíky** (`USE_EXACT_ALARM`) Play povoluje jen appkám typu budík/kalendář/připomínky – tvoje do kategorie spadá, ale je nutné to při odeslání zdůvodnit (jinak zůstat u `SCHEDULE_EXACT_ALARM`, kde si oprávnění zapíná uživatel sám). Dále bude potřeba **privacy policy**, vyplněný formulář **Data safety** a aktuální target API. Rozdělení god-souborů (sekce 2) řešit jen když bude bránit další práci.

---

## Rozhodnutí a doporučení (doplněno 21. 7. po Jendově zpětné vazbě)

Všechny původně otevřené otázky jsi zodpověděl v komentářích – zde je jejich vyřešení:

1. **Google Play je cíl.** Tím se zvedá laťka: přístupnost (sekce 5), minifikace (R8/ProGuard) a řízené zálohování dat přestávají být „až kdyby" a patří do scope před vydáním. Promítnuto do kroku 10 výše, kde jsou i specifické brány Play (poloha na pozadí, přesné budíky).
2. **Mazání swipem → přidat androidí „Vrátit zpět" (snackbar). Rozhodnuto.** Nález v sekci 5 (`ui/ReminderListScreen.kt:415–418`) tím má jasné řešení: po smazání ukázat snackbar s akcí „Vrátit zpět" (cca 4–5 s) a mazání potvrdit teprve po jeho zmizení. Zahrnuto v kroku 6.
3. **Systémová záloha (`allowBackup`) – ptal ses, co je lepší. Moje doporučení: nechat zapnutou, ale řízeně.** Data jsou málo citlivá (tvoje připomínky a názvy míst) a záloha jde jen do tvého vlastního Google účtu (šifrovaně), takže přínos převažuje – připomínky přežijí výměnu telefonu i přeinstalaci, což appka jinak neumí (export/import je mimo scope). Konkrétně: ponechat `allowBackup="true"`, ale doplnit explicitní pravidla zálohy (`data_extraction_rules.xml` pro Android 12+, `full_backup_content` pro starší), abys přesně řídil, co se zálohuje, a měl to podložené pro Play formulář *Data safety*. **Alternativa, když chceš maximum soukromí** (místa Domov/Práce jsou citlivější): `allowBackup="false"` – pak ale uživatel po přeinstalaci o data přijde. Vzhledem k tomu, že appka je zatím hlavně pro tebe, bych volil řízenou zapnutou zálohu.
4. **Přístupnost / TalkBack – ptal ses, co TalkBack vlastně je.** TalkBack je vestavěná **čtečka obrazovky** Androidu pro nevidomé a slabozraké: nahlas předčítá, co je na obrazovce, a ovládá se gesty (uživatel na displej nevidí). Nálezy v sekci 5 znamenají, že s TalkBackem dnes nejde připomínku označit jako hotovou ani smazat a nejdou ovládat přepínače ve formuláři. **Doporučení:** protože míříš na Google Play (= veřejní uživatelé) a opravy jsou levné (doplnit prvkům „roli" a popisky, ne přepisovat appku), udělal bych základní přístupnostní průchod ještě před vydáním. Není to tvrdá podmínka přijetí na Play, ale je to správně a appka tím nikoho nevyloučí. Zůstává v sekci 5 / krocích 5–7.

*Pozn.: Audit je statický (bez sestavení a běhu na zařízení). Nálezy označené „k ověření" – horní inset v dialogu, ořez při velkém písmu, scroll onboardingu – je vhodné potvrdit přímo na tvém Samsungu.*
