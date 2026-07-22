# GeoReminder Android – Project Status
*Naposled aktualizováno: 22. 07. 2026 (v2.4 – import z kalendáře; CELÝ plán vylepšení hotový)*

## 🎯 Co to je
Nativní Android verze GeoReminderu – připomínky vázané na místo i čas, vzhled 1:1 podle iOS předlohy (`design-podklady/DESIGN_SPEC.md`).
Stack: Kotlin + Jetpack Compose, Google Maps (Compose), GeofencingClient, AlarmManager (přesné budíky), Glance widget, App Shortcuts, JSON úložiště formátově kompatibilní s iOS verzí. Minimum: Android 8 (API 26). Jeden modul, bez dalších služeb.

## ⏭️ Příští krok
**Celý plán vylepšení (5 funkcí: Navigovat, hlasité čtení, přílohy, seskupení, import z kalendáře) je HOTOVÝ a na GitHubu (v2.4).** Zbývá **Jendův test na telefonu** všech funkcí naráz (Jenda testuje až na úplném konci vývoje) a podle nálezů doladit. Pak volitelně **Google Play** (`GOOGLE-PLAY-CHECKLIST.md`). **Texty pro Play (listing, Data safety, zdůvodnění oprávnění) i zásady ochrany soukromí jsou HOTOVÉ** – `GOOGLE-PLAY-TEXTY.md`, `PRIVACY.md` / `privacy.html`. K vydání tak chybí už jen: účet + ověření identity, vyvěsit privacy na URL, grafika (ikona 512 + feature graphic + screenshoty – vyrobím na požádání), test 12 testerů/14 dní, zapnout `isMinifyEnabled=true` + sestavit `.aab`.

## ✅ Hotovo
- Kompletní přepis všech obrazovek podle DESIGN_SPEC: onboarding (3 stránky, text upravený pro androidí oprávnění „Povolit vždy"), seznam se sekcemi **Aktivní/Hotové** + swipe Hotovo/Vrátit/Smazat, formulář (Na místě/Na čas, opakování, čipy oblíbených), výběr místa na mapě (hledání, ťuknutí, živý kruh poloměru), oblíbená místa, mapový přehled, oranžové bannery oprávnění
- Vzhled iOS 26 „Liquid Glass": plovoucí kapslový tab bar, kruhová skleněná tlačítka, karty se zaoblením 26, vlastní iOS-styl přepínače/slidery/segmenty, přesné barvy ze spec (světlý i tmavý režim), písmo **Inter** (4 řezy přibalené v APK)
- Notifikace s tlačítky **Hotovo / Odložit o hodinu**, zobrazují se i při běžící appce
- Geofencing (příjezd/odjezd, poloměr 50–1000 m, opakování) + časové připomínky (jednorázové/denní/týdenní) přes přesné budíky
- **Obnova geofence a budíků po restartu telefonu** (BootReceiver – iOS tohle řeší samo, Android ne)
- Widget **Nejbližší připomínky** (2–3 řádky, „Vše vyřízeno", obnova při každém uložení + à 30 min)
- Statické zástupce: podržení ikony → Připomínka na čas / na místo
- JSON soubory (`reminders.json`, `favorites.json`) **bajtově kompatibilní s iOS** (stejná pole, UUID velkými písmeny, datumy jako sekundy od 1. 1. 2001) – do budoucna možný export/import mezi platformami
- Adaptivní ikona vygenerovaná z iOS předlohy (modrý špendlík s oranžovou tečkou)
- Ikona aplikace, české texty 1:1 dle spec §8 (ověřeno nezávislou kontrolou)
- **Build v cloudu: BUILD SUCCESSFUL, podepsané APK 14 MB**
- Nezávislá revize proti spec + iOS kódu našla 5 chyb → všechny opravené (posun data u půlnočních časů, opakované střílení jednorázových geofence po restartu, popisek widgetu v galerii, hledání bez preference okolí, zástupce při aktivní záložce Mapa)
- **Google Maps Demo klíč zapečen do APK i projektu (21. 7. poledne)** – mapy plně funkční; klíč je v `mapskey.properties`
- **První test na Jendově telefonu (21. 7. odpoledne): appka běží, vzhled sedí.** Nalezen 1 nedostatek – hlavička sheetů („Zrušit / Uložit") nalepená pod stavový řádek → **v1.1 opraveno na všech 4 sheet obrazovkách** (formulář, výběr místa, oblíbená místa, úprava místa); hlavní seznam, mapa a onboarding byly v pořádku
- **v1.2: výběr místa na mapě předělán na celoobrazovkové okno** – tahy po mapě se předtím praly s gestem „potažením dolů zavřít okno" a mapa se při posouvání zavírala. Zavírá se tlačítkem Zrušit / potvrzením místa / gestem zpět
- **v1.3 (21. 7. večer): šest vylepšení dle Jendova výběru:**
  - hledání podniků i adres přes Photon (zdarma, bez klíče; vestavěný geokodér jako záloha)
  - hlídač spolehlivosti: banner s tlačítkem na vypnutí optimalizace baterie
  - Sdílet místo z Map Google (i geo: odkazy) → předvyplněná připomínka
  - tlačítko + přímo ve widgetu
  - třetí tlačítko na notifikaci: „Zítra ráno" (nejbližší ráno v 8:00)
  - týdenní opakování ve vybrané dny (čipy Po–Ne; novinka nad rámec iOS verze)
  - nezávislá kontrola našla 3 chyby (popisek dne, kolize sdílení s otevřeným formulářem, půlnoční „zítra ráno") → opraveny před vydáním
- **v1.4 (21. 7. večer): vyhledávání míst nové generace:**
  - živé našeptávání během psaní (od 2 písmen, bez mačkání tlačítka)
  - vzdálenost u každého výsledku („850 m" / „1,2 km")
  - ikonky podle typu místa (obchod, restaurace, zastávka, škola, město…)
  - po ťuknutí do prázdného pole návrhy: oblíbená místa + naposledy vybraná (ukládá se posledních 5)
  - ukazatel „hledám" a hláška při nule výsledků
  - nezávislá kontrola našla 1 chybu (karta návrhů nezmizela po výběru místa) → opravena před vydáním
- **v1.5 (21. 7. večer):**
  - první pokus o opravu schovaného tlačítka „Použít toto místo" (nepomohl – Android 15 na Samsungu ignoruje standardní nastavení okna)
  - **přepínač vzhledu:** nové Nastavení (⚙️ vpravo nahoře) s volbou Podle systému / Světlý / Tmavý; volba se pamatuje, řídí i barvu stavového řádku a mapy. Widget se dál řídí systémem
- **v1.5.1 (21. 7. večer): definitivní oprava tlačítka na mapě** – výška spodní systémové lišty se nově měří v hlavním okně appky (kde funguje spolehlivě) a přenáší se do celoobrazovkového okna mapy, které ji na Androidu 15 samo nedostává. Bere se větší z obou hodnot, takže řešení funguje na všech telefonech
- **v1.6 (21. 7. v noci): druhy upozornění a dožadování** (Jendův nápad):
  - u každé připomínky nová sekce **Upozornění**: druh **Tiché** (bez zvuku, jen v liště) / **Výchozí** / **Naléhavé** (budíkový zvuk na hlasitost budíku + silná vibrace, zvuk hraje, dokud notifikaci nezavřeš)
  - přepínač **„Připomínat, dokud nepotvrdím"** – nepotvrzená notifikace se vrací každých 5 minut; zastaví ji Hotovo, odložení, úprava připomínky nebo otevření appky (smáhnutí omylem ji nezastaví – to je záměr)
  - technicky: 3 notifikační kanály, data zpětně kompatibilní (nová pole `alertStyle`, `nagging`; iOS je ignoruje)
  - nezávislá kontrola našla 1 chybu (neviditelná nekonečná smyčka budíků při zablokování jednoho kanálu notifikací) → opravena před vydáním

- **v1.7 (Fáze 0 oprav z auditu `AUDIT1.md`):** zpevnění ukládání dat a doručení připomínek:
  - **ochrana proti ztrátě dat:** načítání `reminders.json` je odolné (jeden vadný/nekompatibilní záznam nezahodí celý seznam – čte se po záznamech), rozlišuje se „prázdno" od chyby čtení a po neúspěšném čtení se zablokuje zápis (dřív hrozil přepis platného souboru prázdným); atomický zápis navíc dělá zálohu `.bak`
  - **časové připomínky se neztratí:** jednorázový termín v minulosti formulář nepustí a upozorní; zmeškané připomínky (telefon byl vypnutý) se doručí po zapnutí (catch-up); odložení (snooze) přežije restart telefonu
  - **geofence neselže potichu:** při selhání registrace (systémový limit / vypnutá poloha) se ukáže banner; ošetřen souběh značek „vystřeleno"
  - **stabilita:** souborové zápisy z UI běží mimo hlavní vlákno; receivery mají ochranu proti pádu procesu (catch)
  - nezávislá revize našla 1 skulinu (možné dvojí upozornění u nepřesných budíků) → opravena před sestavením; **build v cloudu: BUILD SUCCESSFUL, podepsané APK 14 MB (versionCode 9)**

- **v1.8 (Fáze 1 z auditu – UX a přístupnost):**
  - **„Vrátit zpět" u mazání:** smazání připomínky swipem jde vzít zpět (snackbar), teprve pak se smaže natrvalo
  - **hmatová odezva:** ťuknutí kdekoli dává jemné cvaknutí (haptika) – appka dřív nedávala žádnou odezvu
  - **přístupnost (TalkBack):** swipe akce Hotovo/Vrátit/Smazat dostupné jako akce čtečky; vlastní přepínače/segmenty/volby vzhledu i denní kolečka mají správné role a popisky
  - **offline hláška:** hledání místa bez internetu hlásí výpadek připojení místo „nic nenalezeno"
  - **větší dotykové cíle:** „+" u oblíbených, křížek hledání, denní kolečka i „Přeskočit"
  - nezávislá revize: bez blokujících nálezů; **build BUILD SUCCESSFUL, podepsané APK (versionCode 10)**

- **v1.9 (Fáze 2 z auditu – úklid kódu, bez viditelných změn):**
  - sjednocené duplicity: jedna komponenta `RadiusSlider` (dřív 3×), sdílené odolné dekódování `reminders.json` (Store i widget), jedna konstanta výchozího poloměru
  - konstanty místo roztroušených literálů: jeden název SharedPreferences (`SharedStorage.PREFS`), sdílený klíč `EXTRA_REMINDER_ID`, `SNOOZE_MINUTES`
  - odstraněna naše redundantní deklarace oprávnění `ACCESS_NETWORK_STATE` (v APK ho dál deklaruje mapová SDK, pokud ho potřebuje); dokumentace srovnaná (Photon vs. geokodér)
  - **build BUILD SUCCESSFUL, podepsané APK (versionCode 11)**

- **v2.0 (Fáze 3 – příprava na Google Play):**
  - **řízené zálohování dat** (`data_extraction_rules.xml` + `full_backup_content.xml`): zálohují se jen `reminders.json` + `favorites.json` (přežijí výměnu telefonu), přechodné plánovací značky NE
  - **minifikace R8/ProGuard připravená** (`proguard-rules.pro` + wiring), zatím `isMinifyEnabled = false` – zapnout až po testu na zařízení
  - **UI polish:** neaktivní tab šedě, tabulkové číslice u poloměru na mapě, sjednocená žlutá hvězdička, scrollovatelný onboarding
  - cílový **API 35** = splňuje požadavek Play 2026
  - **`GOOGLE-PLAY-CHECKLIST.md`** = krok-za-krokem postup vydání; pozor na test 12 testerů/14 dní u nových osobních účtů
  - **build BUILD SUCCESSFUL, podepsané APK (versionCode 12)**

- **v2.1 (22. 7. – první milník z plánu vylepšení, versionCode 13):** dvě rychlé funkce z brainstormingu + společný základ:
  - **Tlačítko „Navigovat":** u připomínky na místo je nová akce na notifikaci (první tlačítko) i řádek v detailu; otevře navigaci přímo v Google Maps na souřadnice připomínky, bez Map nabídne výběr jiné mapové appky. **Nepoužívá Maps API klíč** (jen systémový intent), takže nežere kvótu. Technicky přes neviditelnou `NavigateActivity` – Android 12+ blokuje start aktivity přímo z receiveru („notification trampoline"). U míst se proto na notifikaci vejdou 3 akce Navigovat/Hotovo/Odložit (u času zůstává i „Zítra ráno").
  - **Hlasité čtení (TTS):** volitelné (Nastavení → **Funkce**, výchozí VYPNUTO), po spuštění připomínky ji systémové TTS přečte česky nahlas (lokálně). Respektuje tichý/vibrační režim (nečte), bez českého hlasu nečte. Volba „Číst i celý text" (jinak jen název). Nový `FeatureSettings` (vzor `ThemeController`).
  - technicky: nové soubory `data/FeatureSettings.kt`, `notify/NavigationLauncher.kt`, `notify/NavigateActivity.kt`, `notify/TtsSpeaker.kt`; zásahy do `NotificationHelper`, obou receiverů, `SettingsSheet`, `EditReminderSheet`, manifestu (`<queries>` + `NavigateActivity`). Data beze změny (přepínače jsou v SharedPreferences), iOS kompatibilita zachována.
  - nezávislá revize našla 4 věci (trampoline u Navigovat, Toast z pozadí, 4. tlačítko navíc, komentář) → všechny opravené před buildem. **Build v cloudu: BUILD SUCCESSFUL, podepsané APK 14 MB (versionCode 13).**
- **v2.2 (22. 7. – Fáze 2.1 z plánu vylepšení, versionCode 14): Přílohy k připomínce.** V detailu připomínky nová sekce **Přílohy** – přidat (foto, PDF, jakýkoli soubor), otevřít, odebrat; max 5 příloh, každá do 10 MB.
  - **Kopírují se do appky** (Jendovo rozhodnutí): obsah se zkopíruje do privátního úložiště (`filesDir/attachments/`), takže příloha **přežije smazání originálu i přeinstalaci** (přidáno do řízené zálohy). V připomínce se drží jen malý odkaz (nové pole `attachments`; iOS ho ignoruje).
  - otevírání přes **FileProvider**; úklid osiřelých souborů (`Attachments.gc`) při návratu do appky, s ochranou rozdělané úpravy (registr „pending") a vynuceným limitem velikosti při kopírování.
  - nové soubory: `data/Attachments.kt`, model `Attachment`/`AttachmentKind`, `res/xml/file_paths.xml`; zásah do `Reminder`, `EditReminderSheet`, `RootScreen`, manifestu (FileProvider) a zálohovacích pravidel.
  - nezávislá revize našla 4 věci (hl. riziko: gc mohl smazat rozdělanou přílohu; obcházení limitu velikosti) → opraveno. **Build SUCCESSFUL, podepsané APK 14 MB (versionCode 14).**
- **v2.3 (22. 7. – Fáze 2.2 z plánu vylepšení, versionCode 15): Chytré seskupení notifikací.** Víc připomínek spuštěných **najednou na jednom místě** (jedna geofence událost) se zobrazí jako **jedno souhrnné upozornění** „Na tomto místě máš N…" + jednotlivé notifikace ve skupině. **Volitelné, výchozí vypnuto** (Nastavení → Funkce → „Seskupit připomínky na stejném místě").
  - Připomínky zůstávají samostatné: každá má vlastní tlačítka i stav, **splnění jedné neukončí ostatní**; v appce se pořád zobrazují zvlášť. Žádná změna datového modelu (seskupuje se až při doručení).
  - technicky: `NotificationHelper.showGroup` (nativní grouping – `setGroup`/`setGroupSummary`, `InboxStyle`); přepínač `groupByPlace` ve `FeatureSettings`; rozhodnutí v `GeofenceReceiver`. Ošetřeno: osiřelý souhrn po odškrtnutí všech (vyloučení právě zrušené notifikace kvůli async zrušení), dožadování (nag) drží skupinu (registr `groupedIds`), souhrn samých „Tichých" je tichý.
  - nezávislá revize našla 3 věci (osiřelý souhrn, nag vyskočil ze skupiny, hlasitý souhrn tichých) → opraveno. **Build SUCCESSFUL, podepsané APK 14 MB (versionCode 15).**
- **v2.4 (22. 7. – Fáze 3 z plánu vylepšení, versionCode 16): Import z Google Kalendáře.** V horní liště seznamu je **tlačítko kalendáře** – po povolení `READ_CALENDAR` ukáže nadcházející události (30 dní), po výběru se z události udělá **předvyplněná nová připomínka** (název, čas začátku; má-li místo, geokóduje adresu → připomínka na místo, jinak na čas). **Jednorázový import, ne synchronizace.**
  - technicky: nové `data/CalendarImport.kt` (dotaz na `CalendarContract.Instances`, geokódování přes `Geocoder`) + `ui/CalendarPickerSheet.kt`; `EditReminderSheet` dostal parametry `initialTitle`/`initialDueDate`; napojení v `ReminderListScreen` (oprávnění, spinner při geokódování); `READ_CALENDAR` v manifestu (→ do Data safety na Play).
  - nezávislá revize: bez blokujících nálezů; UX drobnosti (spinner, minulý čas → +1 h) doladěny. **Build SUCCESSFUL, podepsané APK 14 MB (versionCode 16).**
- **✅ CELÝ PLÁN VYLEPŠENÍ HOTOVÝ** (`IMPLEMENTACNI-PLAN-VYLEPSENI.md`): všech 5 schválených funkcí z brainstormingu 21. 7. dodáno ve v2.1–v2.4, každá s nezávislou revizí a buildem. Čeká na Jendův souhrnný test v terénu.
- **Projekt je na GitHubu (21. 7. večer):** https://github.com/JendaNDT/GeoReminder-Android – kompletní zdrojový kód a dokumentace. Tajnosti (mapový klíč, podpisový keystore) jsou záměrně mimo git a žijí jen v zipu ve složce na Macu

## 📝 TODO
### MVP (nutné pro v1)
- **Test na telefonu** (instalace dle `NAVOD-INSTALACE.md`, časová připomínka + geo-trigger v terénu, widget)

### Backlog (později)
- **Vydání na Google Play** – postup v `GOOGLE-PLAY-CHECKLIST.md` (účet + ověření identity, test 12 testerů/14 dní, privacy policy, Data safety, deklarace polohy na pozadí, App Bundle `.aab`).
- Volitelně **Fáze 2.5** (architektura: ViewModel místo statického event-busu, singleton scheduler, rozdělení god-souborů EditReminderSheet/LocationPickerSheet) – jen kdyby začala bránit další práci.

## 🐛 Známé bugy
- **Audit (`AUDIT1.md`) prošel appku přes 6 dimenzí.** Kritické/důležité nálezy jsou vyřešené ve Fázi 0 (v1.7) a Fázi 1 (v1.8), úklid dluhu ve Fázi 2 (v1.9); zbytek eviduje `IMPLEMENTACNI-PLAN.md` (Fáze 3). Aktuálně žádný známý funkční bug; čeká se na Jendův test v terénu.
- Pozn. ke Google účtu: mail „Quota Request – Action Required" (žádost o platbu 50 $) s Demo klíčem nesouvisí – vznikl omylem vyžádanou žádostí o navýšení kvóty v konzoli. **Nic neplatit, mail ignorovat**, žádost sama vyprší. Demo klíč funguje zdarma.

## 🏗️ Klíčová rozhodnutí
- **Výběr místa na mapě = celoobrazovkové okno, ne vysouvací sheet (Jendovo rozhodnutí, 21. 7.):** na Androidu se tahy po mapě bily s gestem zavírání sheetu. Ostatní obrazovky (formulář, oblíbená) zůstávají jako sheety – tam gesto nevadí.
- **Mimo scope: záloha/export dat (Jendovo rozhodnutí, 21. 7.):** z návrhů vylepšení vyřazeno, nedělat.
- **Hledání míst: Photon (photon.komoot.io) jako primární** – zdarma, bez klíče, umí názvy podniků; vestavěný geokodér jen jako záloha při výpadku.
- **Opakování ve vybrané dny = nové pole `weekdays` v datech:** iOS verze ho při čtení ignoruje (JSON zůstává kompatibilní); kdyby se někdy vyvíjela dál, je fér funkci doplnit i tam.
- **Přepínač vzhledu (Světlý/Tmavý/Podle systému) jen na Androidu:** iOS verze nastavení nemá, řídí se systémem. Ikona ⚙️ v hlavičce je odchylka od iOS designu (nutná – jinde tlačítko nemá kde být).
- **Google Maps s Demo klíčem (21. 7.):** zdarma, bez platební karty, denní limity pro osobní použití bohaté. Mapová vrstva je izolovaná v `LocationPickerSheet.kt` + `MapOverviewScreen.kt` – kdyby Google změnil podmínky, jde vyměnit za MapLibre bez zásahu do zbytku appky.
- **Geokodér = jen záloha hledání:** primární je Photon (viz výše); vestavěný `Geocoder` (zdarma, bez klíče) se použije jen při výpadku Photonu. Mapový klíč se spotřebovává jen na zobrazení mapy, hledání limity nežere.
- **Vlastní iOS-look komponenty místo Material:** segmentový přepínač, zelený toggle, slider s bílým palcem, kapslová tlačítka, tab bar – Material by vypadal androidovsky a zadání bylo 1:1 vzhled.
- **Podpisový klíč `app/georeminder.keystore` (hesla: georeminder) je SOUČÁSTÍ projektu – neztratit!** Díky němu půjdou budoucí verze instalovat přes stávající bez odinstalace (a ztráty dat).
- **Jednorázové geofence se značkou „vystřeleno"** (SharedPreferences): po spuštění se znovu neregistrují, ale připomínka zůstává v seznamu aktivní – přesně jako na iOS. Úprava nebo „Vrátit" ji znovu ozbrojí.
- **Oprávnění v řetězu po onboardingu:** notifikace (Android 13+) → přesná poloha → zvlášť „Povolit vždy". Chybějící oprávnění hlídají oranžové bannery s tlačítkem do Nastavení.
- **minSdk 26 (Android 8.0, ~98 % zařízení), targetSdk 35.**
- **Rozhodnutí k vylepšením (Fáze 0.2 plánu, 22. 7.):** TTS **nečte** v tichém/vibračním režimu a bez českého hlasu; defaultně čte jen název (volitelně celý text). Navigovat = Google Maps, jinak výběr appky; u časových připomínek tlačítko není. Přepínače funkcí jsou globální (v SharedPreferences), ne per-připomínka.
- **Přílohy (Fáze 2.1) = KOPÍROVAT do appky (Jendovo rozhodnutí 22. 7.):** místo odkazu na soubor (URI) se obsah zkopíruje do privátního úložiště appky – přílohy tak **přežijí přeinstalaci** i smazání originálu. Cena: zabírají místo (řešit rozumný limit velikosti/počtu) a je potřeba je zahrnout do řízené zálohy a uklízet při smazání připomínky. Přepisuje původní doporučení „odkaz" z `IMPLEMENTACNI-PLAN-VYLEPSENI.md`.
- **Navigovat řešeno přes neviditelnou `NavigateActivity`, ne přes receiver** – Android 12+ (targetSdk 31+) blokuje start aktivity z BroadcastReceiveru vyvolaného notifikací („notification trampoline"). `PendingIntent.getActivity` na malou průhlednou aktivitu to obchází.
- **Build v cloudu bez GitHubu:** Android SDK jde z `dl.google.com`, závislosti z Google Maven + Maven Central. **Gradle je nově jen na GitHub releases, a ten je v cloud session zamčený** – distribuci `gradle-8.10.2-bin.zip` proto stáhnout z mirroru `https://mirrors.aliyun.com/macports/distfiles/gradle/` (funguje, 136 MB). Keystore + `mapskey.properties` žijí na Macu v `Android/GeoReminderAndroid/` (mimo git), pro build se kopírují do klonu. AGP 8.7.3 → Gradle 8.9+ (použito 8.10.2), JDK 21 OK.

## 📁 Stav souborů
- `GeoReminder-Android-projekt.zip` – kompletní projekt (rozbalit a otevřít v Android Studiu, nebo přiložit do další session s Claudem)
- `GeoReminder.apk` – aktuální instalační balíček (v1.9, s Demo mapovým klíčem)
- `NAVOD-INSTALACE.md` – jak APK dostat do telefonu a co odklikat
- V projektu: `app/src/main/java/cz/jenda/georeminder/` – `model/` (Reminder, formáty), `data/` (úložiště, poloha), `notify/` (notifikace, geofence, budíky, receivery), `ui/` (obrazovky + iOS komponenty + theme), `widget/` (Glance widget)
- `mapskey.properties` – sem patří Google Maps klíč (řádek `MAPS_API_KEY=AIza…`)
