# Implementační plán – schválená vylepšení GeoReminder Android

*Vychází ze souhrnu brainstormingu (`GeoReminder_souhrn_brainstormingu.pdf`, 21. 7. 2026) a z aktuálního zdrojového kódu na GitHubu (`JendaNDT/GeoReminder-Android`, versionCode 12, v2.0). Pokrývá všech 5 schválených funkcí v pořadí podle doporučené posloupnosti z brainstormingu. Formát je stejný jako u `IMPLEMENTACNI-PLAN.md` (audit).*

---

## Jak plán používat

- **Fáze jdou v pořadí z brainstormingu:** nejdřív upřesnit chování (Fáze 0), pak dodat rychlé přínosy P1, ověřit P2 a nakonec integrovat kalendář P3. První milník = **funkční „Navigovat" + testovací hlasité čtení s globálním přepínačem.**
- **Řídící princip (z brainstormingu, drž se ho u každého bodu):** funkce je **volitelná**, pokud mění chování upozornění; **stávající připomínky zůstávají samostatné a předvídatelné**; přednost mají věci s jasným přínosem a malým zásahem do jádra.
- **Osvědčený postup u tebe:** po každé dávce nechat **nezávislou revizi** (druhý průchod kódem), teprve pak sestavit APK. Držme se toho.
- **Build/podpis:** vždy stejný keystore (`app/georeminder.keystore`, hesla `georeminder`) a **zvyšovat `versionCode`** (teď 12), ať jde APK nainstalovat přes stávající bez ztráty dat.
- **Zpětná kompatibilita dat:** všechna nová pole v `model/Reminder.kt` přidávej **s výchozí hodnotou** a jako „rozšíření Android verze" – JSON čte `SharedStorage.json` s `ignoreUnknownKeys = true` a `encodeDefaults = true`, takže iOS verze nová pole ignoruje a formát zůstává vzájemně čitelný (stejně jako u `alertStyle`, `nagging`, `weekdays`).
- **Značení náročnosti:** 🟢 malá (do ~1 dávky) · 🟡 střední · 🔴 větší zásah (rozmyslet zvlášť).
- **Návrh balení do verzí** je u každé fáze (v2.1, v2.2…). Není povinný.

Legenda u každého bodu: **Co & proč** → **Kde** → **Jak** → **Okrajové stavy** → **Ověření** → náročnost.

---

## Přehled: co která funkce zasáhne

| # | Funkce | Priorita | Nové pole v datech | Nové oprávnění | Klíčové soubory |
|---|---|---|---|---|---|
| 1 | Tlačítko Navigovat | P1 | ne | ne (jen `<queries>`) | `NotificationHelper`, `NotificationActionReceiver`, `EditReminderSheet`, nový `NavigationLauncher` |
| 2 | Hlasité čtení (TTS) | P1 | ne (globální přepínač) | ne | nový `TtsSpeaker`, `NotificationHelper`, receivery, `SettingsSheet` |
| 3 | Přílohy k připomínce | P2 | `attachments` | ne (SAF) | `Reminder`, `EditReminderSheet`, nový `Attachments` |
| 4 | Seskupení notifikací | P2 | ne (děje se při doručení) | ne | `GeofenceReceiver`, `NotificationHelper`, `SettingsSheet` |
| 5 | Import z Google Kalendáře | P3 | ne | `READ_CALENDAR` | nový `CalendarImport` + `CalendarPickerSheet`, `EditReminderSheet`, `AndroidManifest` |

Společné pro P1-TTS a P2-seskupení: **nový přepínačový „ovladač nastavení"** (Fáze 0) po vzoru `ThemeController`.

---

## FÁZE 0 – Upřesnit chování a společný základ · návrh: v2.1

Krátká, ale důležitá fáze. Postaví společnou infrastrukturu přepínačů a písemně rozhodne hraniční stavy dřív, než se začne kódovat – přesně bod „01 Upřesnit chování" z brainstormingu.

### 0.1 Ovladač nastavení funkcí + sekce „Funkce" v Nastavení 🟢
- **Co & proč:** TTS i seskupení jsou **volitelné, výchozí vypnuté**. Potřebují místo, kde se přepínají a kde se stav pamatuje mezi spuštěními. Uděláme jeden sdílený ovladač po vzoru `ThemeController` a jednu novou sekci v Nastavení – tím je hotová infrastruktura pro obě funkce najednou.
- **Kde:**
  - nový `data/FeatureSettings.kt` (vzor: `ui/theme/ThemeController.kt` – `object` + `MutableStateFlow` + `init/set` nad `SharedStorage.PREFS`).
  - `GeoReminderApp.kt:8–12` (přidat `FeatureSettings.init(this)` vedle `ThemeController.init`).
  - `ui/SettingsSheet.kt:56–92` (přidat novou sekci pod „Vzhled").
- **Jak:**
  1. `FeatureSettings`: `MutableStateFlow` pro `readAloud` (Bool, default `false`), `readAloudFullText` (Bool, default `false` = číst jen název), `groupByPlace` (Bool, default `false`). `init(context)` načte z `getSharedPreferences(SharedStorage.PREFS, …)`, `set…(context, value)` uloží (`apply()`) a přepíše StateFlow – 1:1 jako `ThemeController`.
  2. V `SettingsSheet` přidat `SectionHeader("Funkce")` + `InsetCard` s řádky, které používají **stávající** `IOSSwitch`, `CardDivider`, `GeoType`, `colors` (aby to sedělo do iOS vzhledu). Každý přepínač sbírá stav přes `collectAsStateWithLifecycle()`.
  3. Pod každým přepínačem drobná `caption2` s vysvětlením a „výchozí vypnuto".
- **Okrajové stavy:** čtení chybějícího klíče → default `false`. Migrace není potřeba (nové klíče).
- **Ověření:** přepínače se pamatují po restartu appky; ve výchozím stavu jsou všechny vypnuté; vzhled sedí do zbytku Nastavení.
- Náročnost: 🟢

### 0.2 Písemně rozhodnout hraniční stavy (specifikace, ne kód) 🟢
- **Co & proč:** Brainstorming výslovně říká „před implementací určit chování". Levné teď, drahé opravovat později. Není to kód – je to pár rozhodnutí, která níže plán už předjímá; tady je potvrď (nebo změň).
- **Rozhodnout:**
  1. **TTS a hlasitost/režim telefonu:** číst i v tichém/vibračním režimu? *(Návrh: NEČÍST v tichém a vibračním režimu; číst přes kanál hlasitosti oznámení. Naléhavé připomínky výjimku nedělají – TTS je oddělené od budíkového zvuku.)*
  2. **TTS – co číst:** jen název, nebo název + tělo? *(Návrh: default jen název, plný text volitelně přepínačem `readAloudFullText`.)*
  3. **TTS – chybí český hlas:** přeříkat výchozím jazykem, nebo přeskočit? *(Návrh: zkusit `cs-CZ`; když data chybí, jednou nabídnout doinstalování hlasu a pro teď přeskočit – nečíst „anglicky".)*
  4. **Seskupení – rozsah MVP:** seskupit připomínky, které přijdou **v jedné geofence události** (stejný okamžik, stejný typ příjezd/odjezd). Sloučení míst z různých událostí v krátkém okně = pozdější vylepšení. *(Návrh: ano, MVP = jedna událost.)*
  5. **Přílohy – způsob:** odkaz na soubor (trvalé URI oprávnění), **nekopírovat** data; max počet na připomínku. *(Návrh: odkaz, max 5 příloh.)*
  6. **Navigovat – cíl:** Google Maps když jsou k dispozici, jinak systémový výběr mapové aplikace; u časových připomínek tlačítko není. *(Návrh: ano.)*
- **Ověření:** rozhodnutí zapsaná do `PROJECT_STATUS.md` → „Klíčová rozhodnutí".
- Náročnost: 🟢

---

## FÁZE 1 – Rychlé přínosy P1 (první milník) · návrh: v2.2

Dvě funkce, které rozšiřují použití při chůzi/jízdě a jsou snadno pochopitelné. Cíl fáze = **první milník z brainstormingu**.

### 1.1 Tlačítko „Navigovat" 🟢
- **Co & proč:** U připomínky na místo přidat akci, která otevře **Google Maps rovnou s navigací** na souřadnice připomínky. Vysoký přínos, malý zásah. Navigace běží v Mapách, ne v GeoReminderu, a **nepoužívá vlastní Maps API klíč** appky (je to jen systémový intent) – takže nespotřebovává žádnou kvótu Demo klíče.
- **Kde:**
  - nový `notify/NavigationLauncher.kt` – jedno místo, které staví intent + řeší zálohu (volají ho notifikace i UI).
  - `notify/NotificationHelper.kt:149–167` (do `show()` přidat akci jen pro `ReminderKind.LOCATION`) a `:32–34` (nová konstanta `ACTION_NAVIGATE`).
  - `notify/NotificationActionReceiver.kt:20–60` (obsloužit `ACTION_NAVIGATE`: spustit navigaci + zavřít notifikaci).
  - `ui/EditReminderSheet.kt` – v kartě „Kde" (`:279–378`) přidat řádek „Navigovat" jen když `existing != null && kind == LOCATION` (formulář zároveň slouží jako detail připomínky).
  - `AndroidManifest.xml:23–24` – přidat blok `<queries>` (kvůli spolehlivému `resolveActivity` na Androidu 11+).
- **Jak:**
  1. `NavigationLauncher.open(context, lat, lng, placeName)`:
     - primárně `Intent(ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lng"))` s `setPackage("com.google.android.apps.maps")` a `FLAG_ACTIVITY_NEW_TASK`.
     - když se nedá rozlišit (Mapy nejsou) → `Intent(ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(placeName)})"))` bez `setPackage` → `Intent.createChooser(...)` (jakákoli mapová appka).
     - obal `try/catch (ActivityNotFoundException)` → Toast „Není nainstalovaná žádná mapová aplikace".
  2. V notifikaci: v `show()` přidat `.addAction(0, "Navigovat", navPendingIntent)` **jen** pro `LOCATION`. `navPendingIntent` = `getBroadcast` na `NotificationActionReceiver` s `ACTION_NAVIGATE` a `EXTRA_REMINDER_ID` (jako stávající akce; použij nový unikátní requestCode, např. `notifId + 4`).
  3. V `NotificationActionReceiver`: pro `ACTION_NAVIGATE` najít připomínku, zavolat `NavigationLauncher.open(...)` s jejími souřadnicemi, pak `NotificationHelper.cancel`. (Receiver dovoluje ošetřit zálohu; přímý `getActivity` by ji neuměl.)
  4. V `EditReminderSheet` řádek „Navigovat" (ikona `Icons.Filled.Navigation`) volá `NavigationLauncher.open` přímo.
  5. `<queries>` v manifestu: položka pro `com.google.android.apps.maps` a pro schéma `geo` – jinak `resolveActivity()` na API 30+ vrací `null`, i když mapa existuje.
- **Okrajové stavy:** Mapy vypnuté/odinstalované → výběr jiné mapové appky; žádná mapová appka → Toast, nic nespadne. Časová připomínka → akce se nepřidá. Souřadnice 0,0 (nemělo by nastat u uložené místní připomínky) → ošetřit brzkým returnem.
- **Ověření:** notifikace u místní připomínky ukáže „Navigovat" → otevře navigaci Google Maps na bod; dtto řádek v detailu; po vypnutí Map se ukáže výběr appky; časové připomínky tlačítko nemají.
- Náročnost: 🟢

### 1.2 Hlasité přečtení textu připomínky (TTS) 🟡
- **Co & proč:** Po spuštění upozornění může Android **Text-to-Speech** přečíst název (volitelně i celý text). Rozšiřuje použití při chůzi/jízdě, kdy uživatel nechce koukat do telefonu. **Volitelné, výchozí vypnuté** (přepínač z Fáze 0.1). Text se zpracuje **lokálně** systémovým TTS – nic neodchází ven.
- **Kde:**
  - nový `notify/TtsSpeaker.kt` – obálka nad `android.speech.tts.TextToSpeech` (init, čeština, `speak`, `shutdown`).
  - `notify/NotificationHelper.kt:174–191` (po `notify(...)` volitelně přečíst).
  - `notify/GeofenceReceiver.kt:50`, `notify/AlarmReceiver.kt:39`, `notify/ReminderScheduler.kt:258` (místa, kde se volá `show()` – tady se musí počkat na dořečení, viz níže).
  - `data/FeatureSettings.kt` (přepínače `readAloud`, `readAloudFullText`), `ui/SettingsSheet.kt` (řádky).
- **Jak:**
  1. `TtsSpeaker`: `suspend fun speak(context, text)` – vytvoří `TextToSpeech`, počká na `onInit` (přes `suspendCancellableCoroutine`), nastaví `Locale("cs","CZ")` (ověř `isLanguageAvailable`), `setAudioAttributes` s `USAGE_NOTIFICATION`, spustí `speak(text, QUEUE_FLUSH, params, utteranceId)`, přes `UtteranceProgressListener.onDone/onError` dokončí a **`shutdown()`**. Časový strop ~10 s.
  2. **Lifecycle (nejcitlivější místo):** TTS engine se inicializuje asynchronně, ale receivery běží přes `goAsync()` a proces může být po `pending.finish()` zabit. Proto TTS **volat uvnitř `CoroutineScope(Dispatchers.IO).launch { … }`** receiverů a `pending.finish()` zavolat až **po** dokončení řeči (nebo po timeoutu). Nejčistší: `NotificationHelper` dostane `suspend fun showAndSpeak(...)` nebo receivery po `show()` zavolají `TtsSpeaker.speak(...)` ještě před `finally { pending.finish() }`.
  3. Podmínka čtení: `FeatureSettings.readAloud.value == true` **a** kanál není ztlumený **a** (dle 0.2) telefon není v tichém/vibračním režimu (`AudioManager.ringerMode == RINGER_MODE_NORMAL`).
  4. Text: `reminder.title` (+ `NotificationHelper.body(reminder)` když `readAloudFullText`). Ořezat na rozumnou délku.
  5. Přepínače v Nastavení: „Číst připomínky nahlas" + pod ním (jen když zapnuto) „Číst i celý text".
- **Okrajové stavy:** tichý/vibrační režim → nečíst (dle 0.2). Probíhající hovor / jiné audio → `speak` počká na audio focus, případně přeskočí. Sluchátka/Bluetooth → TTS jde automaticky do aktivního výstupu (ověřit na zařízení). Chybí český hlas → jednou nabídnout doinstalování (`ACTION_INSTALL_TTS_DATA`), teď přeskočit. Více připomínek naráz (i se seskupením 4) → `QUEUE_FLUSH` = přečíst jen poslední, nebo `QUEUE_ADD` = fronta; MVP `QUEUE_FLUSH`.
- **Ověření:** zapnout přepínač → po spuštění připomínky telefon přečte název česky; vypnout → ticho; otestovat sluchátka, tichý režim, chybějící český hlas. (Toto je „prototyp" z brainstormingu – první verze může být jednoduchá, dolaď podle testu.)
- Náročnost: 🟡

**Po Fázi 1: nezávislá revize → build → test na telefonu. Tady je první milník.**

---

## FÁZE 2 – Ověřit P2 (malý rozsah MVP) · návrh: v2.3

Dvě funkce s vyšší náročností. Dělat je až po ověřeném P1. Držet **malý MVP rozsah** (brainstorming), aby se datový model a doručování zbytečně nekomplikovaly.

### 2.1 Přílohy k připomínce 🟡🔴
- **Co & proč:** K připomínce lze připojit materiál (foto účtenky/výrobku, PDF/formulář, QR obrázek), který je potřeba otevřít přímo na místě. **První verze:** přidat, zobrazit, otevřít, odebrat. Použít **trvalý přístup k vybranému souboru** (URI oprávnění), **nekopírovat** velká data.
- **Kde:**
  - `model/Reminder.kt:104–139` – nové pole `attachments: List<Attachment> = emptyList()` + nový `@Serializable data class Attachment(val uri: String = "", val name: String = "", val mime: String = "", val kind: AttachmentKind = AttachmentKind.FILE)` a `enum AttachmentKind { PHOTO, DOCUMENT, QR, FILE }`. (Rozšíření Android verze – iOS ignoruje.)
  - nový `data/Attachments.kt` – `takePersistableUriPermission`, `release…`, čtení názvu/typu přes `ContentResolver`, otevření přes `ACTION_VIEW`.
  - `ui/EditReminderSheet.kt` – nová sekce „Přílohy" (tlačítko „Přidat" + seznam s otevřením/odebráním); v `save()` (`:157–227`) přenést `attachments` do kopírované/nové připomínky.
  - `data/ReminderStore.kt:105–110` (`delete`) – při smazání připomínky uvolnit URI oprávnění příloh.
  - `proguard-rules.pro` – až se zapne minifikace, přidat keep pravidlo pro nový `@Serializable` typ (kotlinx.serialization).
- **Jak:**
  1. Výběr souboru: `rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument())` (ne `GetContent` – `OpenDocument` umožní **trvalé** oprávnění). Filtr MIME `arrayOf("image/*","application/pdf","*/*")`.
  2. Po výběru: `contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)`; zjistit `name` (`OpenableColumns.DISPLAY_NAME`) a `mime` (`contentResolver.getType`); odvodit `kind`; přidat `Attachment` do seznamu ve stavu formuláře.
  3. Uložení: v JSON je `uri.toString()` (`content://…`). Díky trvalému oprávnění přežije restart. **Nekopírovat** obsah (dle brainstormingu).
  4. Zobrazení v `EditReminderSheet`: řádky/čipy s ikonou dle `kind`, ťuknutí → `Intent(ACTION_VIEW, uri).addFlags(FLAG_GRANT_READ_URI_PERMISSION)` v chooseru. Křížek → `releasePersistableUriPermission` + odebrat ze seznamu.
  5. Smazání připomínky: uvolnit všechna oprávnění jejích příloh (systém drží omezený počet trvalých URI – nenechávat viset).
  6. QR: první verze = jen obrázek s `kind = QR`. Skenování/generování později (mimo scope).
- **Okrajové stavy:** zdrojový soubor smazán/přesunut → otevření selže → hláška „Přílohu se nepodařilo otevřít (soubor už nemusí existovat)" + nabídnout odebrání. **Přeinstalace appky** → trvalá URI oprávnění se ztrácí (jsou vázaná na instalaci) → přílohy přestanou jít otevřít, i když se `reminders.json` obnoví ze zálohy; **napsat to uživateli** jako známé omezení první verze. Cloud poskytovatel (Drive) → otevření vyžaduje síť. Limit počtu příloh (5) na připomínku.
- **Ověření:** přidat foto a PDF → zobrazí se se správným názvem/ikonou; restart appky/telefonu → jdou pořád otevřít; odebrání uvolní oprávnění; smazání zdrojového souboru dá srozumitelnou chybu, ne pád.
- Náročnost: 🟡🔴

### 2.2 Chytré seskupení notifikací 🔴
- **Co & proč:** Víc připomínek na stejném místě se zobrazí jako **jedno přehledné upozornění** („Na tomto místě máš 3 připomínky"), po rozbalení jednotlivé. **Volitelné, výchozí vypnuté.** Samotné připomínky zůstávají oddělené: každá má vlastní stav a akce, splnění jedné neukončí ostatní, v appce se pořád zobrazují samostatně. **Seskupovat až při doručení** – žádná změna datového modelu.
- **Kde:**
  - `notify/GeofenceReceiver.kt:39–59` – dnes v cyklu volá `NotificationHelper.show()` per připomínka; tady se rozhodne o skupině.
  - `notify/NotificationHelper.kt` – nová `showGroup(context, reminders)` (souhrn + jednotlivé přes `setGroup`).
  - `data/FeatureSettings.kt` (`groupByPlace`), `ui/SettingsSheet.kt` (řádek).
- **Jak:**
  1. Přepínač `groupByPlace` (default `false`) z Fáze 0.1.
  2. V `GeofenceReceiver` po odfiltrování shodujících se připomínek (už teď filtruje transition + `!isDone` + `LOCATION`): když `groupByPlace` a počet `>= 2`, zavolat `NotificationHelper.showGroup(context, matches)`; jinak per připomínka `show()` jako dnes. (MVP dle 0.2 = připomínky z **jedné** geofence události = stejné místo/okamžik/typ.)
  3. `showGroup` využije **nativní seskupení notifikací** Androidu:
     - každou připomínku zobrazit jako dnes (`show()`), ale s `.setGroup(GROUP_KEY)` – zachová si vlastní `notifId` (`reminder.id.hashCode()`) i vlastní akce (Hotovo/Odložit/Navigovat).
     - jednu **souhrnnou** notifikaci s `.setGroup(GROUP_KEY).setGroupSummary(true)`, `InboxStyle` se seznamem názvů, titulek „Na tomto místě máš N připomínek", samostatný `notifId`.
  4. „Splnění jedné neukončí ostatní" platí automaticky – každá dětská notifikace má vlastní `ACTION_DONE` a `EXTRA_REMINDER_ID`. Po Hotovo se zruší jen ta jedna.
  5. Interakce s **dožadováním** (`nagging`) a **naléhavým** stylem: rozhodnout, zda ve skupině tlumit nag (jinak hluk); naléhavý zvuk zůstává u dané dětské notifikace. *(Návrh: nag ve skupině ponechat, ale ověřit hlukovost na zařízení; klidně doladit.)*
- **Okrajové stavy:** jedna připomínka → normální notifikace (bez souhrnu). Různé typy (příjezd vs odjezd) na stejném místě → různé skupiny (dle 0.2). Smazání souhrnu smaže i děti (chování Androidu) → přijatelné. Různé kanály/styly ve skupině → děti si nesou vlastní kanál, souhrn na výchozím. Časové připomínky ve stejnou minutu → mimo scope první verze (jen místní).
- **Ověření:** dvě místní připomínky s překrývajícími se geofence, přepínač ON → jeden souhrn „máš 2 připomínky", rozbalení ukáže obě s vlastními tlačítky; Hotovo u jedné → druhá zůstává; přepínač OFF → dvě samostatné notifikace jako dřív.
- Náročnost: 🔴 (sémantika seskupení + souběh s nag/urgent – proto až po P1 a pečlivě otestovat)

**Po Fázi 2: nezávislá revize → build → test na telefonu.**

---

## FÁZE 3 – Integrace kalendáře P3 · návrh: v2.4

Nejväčší jednotlivá funkce, proto poslední. **Jednorázový import, ne obousměrná synchronizace.**

### 3.1 Import vybrané události z Google Kalendáře 🔴
- **Co & proč:** Uživatel vybere jednu událost. GeoReminder převezme **název, čas a případné místo** a nabídne převod. Časová připomínka převezme čas začátku; místní připomínka převede adresu na souřadnice a nechá uživatele **potvrdit bod i poloměr**. Zkracuje zadávání a propojuje událost s připomínkou.
- **Kde:**
  - `AndroidManifest.xml:9–24` – přidat `<uses-permission android:name="android.permission.READ_CALENDAR" />` (runtime oprávnění).
  - nový `data/CalendarImport.kt` – dotaz na `CalendarContract.Instances` přes `ContentResolver`.
  - nový `ui/CalendarPickerSheet.kt` – seznam nadcházejících událostí k výběru (vzhled jako ostatní sheety: `SheetHeader`, `InsetCard`…).
  - vstupní bod: `ui/ReminderListScreen.kt` (u „+"/nové připomínky přidat volbu „Importovat z kalendáře"), nebo přes event-bus jako `MainActivity.shortcutRequest`/`sharedPlaceText` (`MainActivity.kt:15–21`).
  - `ui/EditReminderSheet.kt:93–99` – přidat parametry `initialTitle` (a využít stávající `initialKind/initialPlaceName/initialCoordinate`; pro čas přidat `initialDueDate`), aby šel formulář předvyplnit.
  - geokódování adresy: znovupoužít přístup z hledání míst (**Photon primární, `Geocoder` záloha** – viz „Klíčová rozhodnutí") a `ui/LocationPickerSheet.kt` na potvrzení bodu + poloměru (jeho `onConfirm(name, coord, radius)`).
- **Jak:**
  1. Manifest + runtime žádost o `READ_CALENDAR` ve chvíli, kdy uživatel ťukne „Importovat z kalendáře" (`rememberLauncherForActivityResult(RequestPermission())`).
  2. `CalendarImport`: `contentResolver.query(CalendarContract.Instances.CONTENT_URI …)` na rozsah `now .. now+30 dní`, sloupce `TITLE`, `BEGIN`, `EVENT_LOCATION`, `ALL_DAY`, `CALENDAR_DISPLAY_NAME`; seřadit dle `BEGIN`. Vrátit lehký model událostí.
  3. `CalendarPickerSheet`: seznam událostí; po výběru:
     - když má událost místo → zeptat se „Na čas / Na místo"; bez místa → jen „Na čas".
     - **Na čas:** předvyplnit `EditReminderSheet` s `kind = TIME`, `initialTitle = title`, `initialDueDate = begin`.
     - **Na místo:** geokódovat `EVENT_LOCATION` → `LatLng` (Photon → Geocoder). Otevřít `LocationPickerSheet` vycentrovaný na bod s výchozím poloměrem; uživatel potvrdí bod + poloměr; pak `EditReminderSheet` s `kind = LOCATION`, `initialTitle`, `initialPlaceName`, `initialCoordinate`.
  4. **Jednorázový import:** po vytvoření připomínky se nedrží žádná vazba na kalendář (žádná synchronizace) – napsat i do UI, ať je to jasné.
  5. Vstupní bod: nejjednodušší je volba u „+" v `ReminderListScreen` (stejný stavový vzor jako `showNewSheet`/`editingReminder`, `:117,380–405`).
- **Okrajové stavy:** oprávnění zamítnuto → vysvětlení + odkaz do Nastavení. Žádný účet/žádné události → prázdný stav. Událost bez místa → jen čas. **Celodenní událost** → `BEGIN` je půlnoc → nabídnout výchozí čas (např. 9:00) a nechat upravit. Geokódování selže (vágní adresa) → nechat vybrat ručně na mapě (`LocationPickerSheet` už umí hledání/ťuknutí). Opakovaná událost → importovat jen vybraný výskyt. Víc kalendářů → volitelně označit názvem kalendáře. Časové zóny → `CalendarContract` dává UTC milis, převést do lokálního.
- **Ověření:** událost s místem → import → „Na místo" → adresa se geokóduje, potvrdit na mapě → připomínka vznikne na čas začátku / na místo; událost bez místa → jen čas; zamítnuté oprávnění → srozumitelná hláška.
- Náročnost: 🔴

**Po Fázi 3: nezávislá revize → build → test na telefonu.**

---

## Průřezové věci

### Nová oprávnění a dopad na Google Play
- **`READ_CALENDAR`** (jen funkce 5): v konzoli Play je nutné deklarovat v **Data safety** a mít v privacy policy. Nespadá do citlivých oprávnění polohy, ale řádek do Data safety patří.
- **Přílohy přes SAF** (funkce 3): **nepotřebují** žádné úložné oprávnění (`READ_EXTERNAL_STORAGE` ne) – `OpenDocument` dává přístup per soubor. To je pro Play ideální (žádné široké úložné oprávnění k obhajobě).
- **Navigovat/TTS:** žádné nové oprávnění. `<queries>` pro Navigovat je jen viditelnost balíčků, ne oprávnění.
- Poloha na pozadí a přesné budíky se tímto nemění (řeší `GOOGLE-PLAY-CHECKLIST.md`).

### Zpětná kompatibilita dat (iOS ↔ Android)
- Jen funkce 3 přidává pole (`attachments`). Přidat s defaultem `emptyList()`; iOS ho přes `ignoreUnknownKeys` ignoruje, formát zůstává čitelný oběma. Funkce 1, 2, 4 nemění model; 2 a 4 se řídí globálními přepínači v `SharedPreferences` (mimo `reminders.json`).
- Zachovat konvence: UUID velkými písmeny, datumy přes `AppleDateSerializer`. Příloh se to netýká (jsou to `content://` řetězce).

### Minifikace (R8)
- Je připravená, ale vypnutá (`app/build.gradle.kts:48`). Až se zapne, nový `@Serializable Attachment` potřebuje keep pravidlo (kotlinx.serialization) v `proguard-rules.pro`, jinak by se JSON příloh rozbil. `enum`y jsou v pořádku, ale ověřit.

### Testovací checklist na zařízení (po každé fázi)
- **Navigovat:** místní připomínka → notifikace i detail spustí navigaci; bez Map výběr appky.
- **TTS:** čte česky; respektuje tichý režim; sluchátka/Bluetooth; chybějící hlas.
- **Přílohy:** foto + PDF; přežijí restart; otevření po smazání zdroje nespadne.
- **Seskupení:** 2+ připomínky na místě → souhrn; Hotovo u jedné nechá druhou; vypnutý přepínač = staré chování.
- **Kalendář:** import na čas i na místo; celodenní událost; zamítnuté oprávnění.
- Průřezově: **žádná regrese** existujících připomínek (jádro se nemění), svižnost, TalkBack u nových prvků (přepínače role `Switch`, tlačítka `contentDescription` – navazuje na přístupnostní práci z v1.8).

---

## Rychlý přehled priorit

| Fáze | Verze | Co | Náročnost |
|---|---|---|---|
| **0** | v2.1 | Ovladač nastavení + sekce „Funkce"; rozhodnout hraniční stavy | 🟢 |
| **1** | v2.2 | **P1:** Navigovat 🟢 + TTS 🟡 → **první milník** | 🟢🟡 |
| **2** | v2.3 | **P2:** Přílohy 🟡🔴 + Seskupení notifikací 🔴 | 🔴 |
| **3** | v2.4 | **P3:** Import z Google Kalendáře 🔴 | 🔴 |

**Doporučené pořadí prvních kroků:** 0.1 (přepínače) → 1.1 (Navigovat) → 1.2 (TTS prototyp) → test na telefonu → 2.1 → 2.2 → 3.1.

*Odložené nápady z brainstormingu (chytrá priorita podle polohy, oblast místo bodu, závislé připomínky, opakované dožadování, rychlé přidání z plochy) do tohoto plánu záměrně nepatří – buď byly zamítnuty, odloženy, nebo už v appce existují.*

*Až budeš chtít, vezmu kterýkoli bod (nebo celou fázi) a rovnou ho naimplementuju – stačí říct číslo. Po každé dávce udělám nezávislou revizi, než sestavíme APK.*
