# GeoReminder Android – Project Status
*Naposled aktualizováno: 21. 07. 2026 v noci (v1.6 – druhy upozornění a dožadování)*

## 🎯 Co to je
Nativní Android verze GeoReminderu – připomínky vázané na místo i čas, vzhled 1:1 podle iOS předlohy (`design-podklady/DESIGN_SPEC.md`).
Stack: Kotlin + Jetpack Compose, Google Maps (Compose), GeofencingClient, AlarmManager (přesné budíky), Glance widget, App Shortcuts, JSON úložiště formátově kompatibilní s iOS verzí. Minimum: Android 8 (API 26). Jeden modul, bez dalších služeb.

## ⏭️ Příští krok
**Otestovat v1.5:** hlavně že je na mapě po výběru místa vidět tlačítko „Použít toto místo" (Jendou nahlášená chyba), nový přepínač vzhledu (⚙️ vpravo nahoře → Vzhled), živé našeptávání s vzdálenostmi a ikonkami. Plus zbylé novinky z v1.3 (sdílení z Map, widget +, „Zítra ráno", opakování ve vybrané dny) a pořád geo-trigger v terénu.

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

- **Projekt je na GitHubu (21. 7. večer):** https://github.com/JendaNDT/GeoReminder-Android – kompletní zdrojový kód a dokumentace. Tajnosti (mapový klíč, podpisový keystore) jsou záměrně mimo git a žijí jen v zipu ve složce na Macu

## 📝 TODO
### MVP (nutné pro v1)
- **Test na telefonu** (instalace dle `NAVOD-INSTALACE.md`, časová připomínka + geo-trigger v terénu, widget)

### Backlog (později)
- Vydání na Google Play (jednorázově 25 $ vývojářský účet)

## 🐛 Známé bugy
- Žádné známé bugy. Netestováno zatím na fyzickém zařízení – první kolo testů může něco odhalit.
- Pozn. ke Google účtu: mail „Quota Request – Action Required" (žádost o platbu 50 $) s Demo klíčem nesouvisí – vznikl omylem vyžádanou žádostí o navýšení kvóty v konzoli. **Nic neplatit, mail ignorovat**, žádost sama vyprší. Demo klíč funguje zdarma.

## 🏗️ Klíčová rozhodnutí
- **Výběr místa na mapě = celoobrazovkové okno, ne vysouvací sheet (Jendovo rozhodnutí, 21. 7.):** na Androidu se tahy po mapě bily s gestem zavírání sheetu. Ostatní obrazovky (formulář, oblíbená) zůstávají jako sheety – tam gesto nevadí.
- **Mimo scope: záloha/export dat (Jendovo rozhodnutí, 21. 7.):** z návrhů vylepšení vyřazeno, nedělat.
- **Hledání míst: Photon (photon.komoot.io) jako primární** – zdarma, bez klíče, umí názvy podniků; vestavěný geokodér jen jako záloha při výpadku.
- **Opakování ve vybrané dny = nové pole `weekdays` v datech:** iOS verze ho při čtení ignoruje (JSON zůstává kompatibilní); kdyby se někdy vyvíjela dál, je fér funkci doplnit i tam.
- **Přepínač vzhledu (Světlý/Tmavý/Podle systému) jen na Androidu:** iOS verze nastavení nemá, řídí se systémem. Ikona ⚙️ v hlavičce je odchylka od iOS designu (nutná – jinde tlačítko nemá kde být).
- **Google Maps s Demo klíčem (21. 7.):** zdarma, bez platební karty, denní limity pro osobní použití bohaté. Mapová vrstva je izolovaná v `LocationPickerSheet.kt` + `MapOverviewScreen.kt` – kdyby Google změnil podmínky, jde vyměnit za MapLibre bez zásahu do zbytku appky.
- **Hledání míst přes vestavěný `Geocoder` (zdarma, bez klíče):** mapový klíč se spotřebovává jen na zobrazení mapy, hledání limity nežere. Hledá přednostně do ~30 km od uživatele, pak celosvětově.
- **Vlastní iOS-look komponenty místo Material:** segmentový přepínač, zelený toggle, slider s bílým palcem, kapslová tlačítka, tab bar – Material by vypadal androidovsky a zadání bylo 1:1 vzhled.
- **Podpisový klíč `app/georeminder.keystore` (hesla: georeminder) je SOUČÁSTÍ projektu – neztratit!** Díky němu půjdou budoucí verze instalovat přes stávající bez odinstalace (a ztráty dat).
- **Jednorázové geofence se značkou „vystřeleno"** (SharedPreferences): po spuštění se znovu neregistrují, ale připomínka zůstává v seznamu aktivní – přesně jako na iOS. Úprava nebo „Vrátit" ji znovu ozbrojí.
- **Oprávnění v řetězu po onboardingu:** notifikace (Android 13+) → přesná poloha → zvlášť „Povolit vždy". Chybějící oprávnění hlídají oranžové bannery s tlačítkem do Nastavení.
- **minSdk 26 (Android 8.0, ~98 % zařízení), targetSdk 35.**

## 📁 Stav souborů
- `GeoReminder-Android-projekt.zip` – kompletní projekt (rozbalit a otevřít v Android Studiu, nebo přiložit do další session s Claudem)
- `GeoReminder.apk` – instalační balíček (zatím bez mapového klíče)
- `NAVOD-INSTALACE.md` – jak APK dostat do telefonu a co odklikat
- V projektu: `app/src/main/java/cz/jenda/georeminder/` – `model/` (Reminder, formáty), `data/` (úložiště, poloha), `notify/` (notifikace, geofence, budíky, receivery), `ui/` (obrazovky + iOS komponenty + theme), `widget/` (Glance widget)
- `mapskey.properties` – sem patří Google Maps klíč (řádek `MAPS_API_KEY=AIza…`)
