# GeoReminder – Google Play release checklist

**Aktualizováno:** 8. 9. 2026  
**Build:** versionName 2.7, versionCode 19, targetSdk 36  
**Stav:** kódová stabilizace je téměř hotová; před veřejným releasem zbývá release/R8 hardening, device matrix a Play Console proces.

> Tento checklist odděluje věci ověřitelné v repozitáři od kroků, které musí proběhnout v Google Play Console nebo na reálném zařízení. Zelený CI build není totéž jako schválená produkční aplikace. Android i Play Console mají v tomto směru pozoruhodně vyvinutý smysl pro byrokracii.

## 1. Technický release gate

- [x] minSdk 26
- [x] compileSdk 36
- [x] targetSdk 36
- [x] versionName/versionCode jsou konzistentní: 2.7 / 19
- [x] JVM unit testy
- [x] API 36 instrumentation testy – GitHub Actions run #166 (`34162208388`)
- [x] CI pro PR do `main` a push do `main`
- [ ] `lintRelease` zelený na finálním headu
- [ ] `assembleRelease` zelený na finálním headu
- [ ] R8/minifikovaný release zelený
- [ ] minifikovaný build ručně ověřen na zařízení
- [ ] Samsung/One UI sloupec z `DEVICE-TEST-MATRIX.md`
- [ ] čistý Android/Pixel sloupec z `DEVICE-TEST-MATRIX.md`

### Aktuální Play target API pravidlo

Od **31. srpna 2026** musí nové mobilní Android aplikace i aktualizace odesílané na Google Play cílit na **Android 16 / API 36 nebo vyšší**. GeoReminder už `targetSdk = 36` používá.

Zdroj: https://support.google.com/googleplay/android-developer/answer/11926878

## 2. Release bundle a podpis

Google Play distribuce má používat **Android App Bundle (`.aab`)**.

Před closed trackem:

- [ ] zvýšit versionCode pro konkrétní upload, pokud už code 19 někde na Play existuje,
- [ ] sestavit `bundleRelease`,
- [ ] podepsat upload key z `app/georeminder.keystore`,
- [ ] bezpečně zálohovat upload key a hesla mimo Git,
- [ ] ověřit výsledný bundle v Play Console/internal tracku.

`mapskey.properties` ani keystore nejsou v repozitáři a nemají se do něj přidávat.

## 3. Closed testing pro nový osobní účet

Pro **osobní vývojářské účty vytvořené po 13. 11. 2023** Google před přístupem do produkce požaduje closed test s minimálně **12 testery**, kteří jsou nepřetržitě přihlášeni do testu alespoň **14 dní**. Potom se žádá o production access v Play Console.

- [ ] ověřit typ a datum založení konkrétního developer účtu,
- [ ] založit closed testing track,
- [ ] přidat minimálně 12 testerů, pokud se na účet podmínka vztahuje,
- [ ] nahrát release `.aab`,
- [ ] udržet požadovaný počet testerů opt-in 14 dní,
- [ ] po splnění podat žádost o production access.

Zdroj: https://support.google.com/googleplay/android-developer/answer/14151465

## 4. Background location

GeoReminder deklaruje:

- `ACCESS_COARSE_LOCATION`
- `ACCESS_FINE_LOCATION`
- `ACCESS_BACKGROUND_LOCATION`

Background location je základní součást location reminderů, protože geofence musí fungovat i bez otevřené Activity.

Pro Play review:

- [ ] prominent disclosure v aplikaci odpovídá skutečnému použití,
- [ ] Play Console Location permissions declaration je vyplněná,
- [ ] zdůvodnění vysvětluje konkrétní uživatelskou funkci „připomeň mi při příchodu/odchodu“,
- [ ] případné demonstrační video ukazuje skutečný tok aplikace,
- [ ] požadovaný rozsah oprávnění je minimální pro danou funkci,
- [ ] privacy policy popisuje background location.

Google vyžaduje location declaration, pokud bundle cílí na Android 10+ a obsahuje `ACCESS_BACKGROUND_LOCATION`.

Zdroj: https://support.google.com/googleplay/android-developer/answer/9799150

## 5. Přesné alarmy

GeoReminder používá:

`android.permission.SCHEDULE_EXACT_ALARM`

Nepoužívá omezené `USE_EXACT_ALARM`.

To odpovídá současné architektuře: uživatel udělí special access a aplikace při jeho absenci umí degradovat na nepřesný alarm a stav zobrazí v diagnostice.

- [x] `SCHEDULE_EXACT_ALARM` v manifestu,
- [x] stav přístupu viditelný v aplikaci,
- [x] aplikace umí otevřít správnou systémovou obrazovku,
- [x] fallback při nepovoleném exact alarm access,
- [ ] při Play review ověřit, zda Console pro konkrétní bundle vyžádá další formulář/deklaraci.

Google omezuje zejména `USE_EXACT_ALARM`; pokud appka nepotřebuje tento omezený typ oprávnění, doporučuje použít `SCHEDULE_EXACT_ALARM` s uživatelsky uděleným přístupem.

Zdroj: https://support.google.com/googleplay/android-developer/answer/16909972

## 6. Data safety

Skutečné chování GeoReminderu je potřeba přepsat do Data safety formuláře bez marketingové poezie.

Aplikace zpracovává zejména:

- přesnou/přibližnou polohu pro geofencing,
- uživatelem zadané názvy/text reminderů,
- adresy/souřadnice uložených míst,
- volitelně JPEG/PNG/PDF přílohy,
- volitelně kalendářové údaje při jednorázovém importu.

Aplikace nemá vlastní uživatelský účet, reklamy ani vlastní analytický backend. Některé funkce používají třetí strany: Google Maps/Play Services, Photon/OpenStreetMap a systémové geokódování. Android cloud backup/device transfer může zálohovat reminder/favorites data a spravované přílohy do uživatelova účtu podle systémového nastavení.

Před odesláním:

- [ ] projít každý Data safety dotaz podle aktuální definice „collected/shared“ v Play Console,
- [ ] neoznačovat data automaticky jako „necollectovaná“ jen proto, že je nevidí provozovatel aplikace; Play definice se řídí i přenosem ke třetím stranám,
- [ ] uvést účely funkcí pravdivě,
- [ ] ověřit šifrování přenosu u síťových služeb,
- [ ] porovnat finální formulář s `PRIVACY.md`.

## 7. Privacy policy

Soubor `PRIVACY.md` musí odpovídat finálnímu release buildu a musí být dostupný na veřejné URL použitelné z Play Console.

- [x] popisuje location/background location,
- [x] popisuje mapy a Photon,
- [x] popisuje kalendář,
- [x] popisuje Android backup,
- [x] uvádí, že aplikace nemá reklamy ani vlastní analytiku,
- [ ] publikovat aktuální verzi na veřejné URL,
- [ ] vložit URL do Play Console,
- [ ] před uploadem znovu porovnat text se skutečným chováním release buildu.

## 8. Store listing

Před closed/public trackem ověřit, že listing neslibuje nic, co aplikace neumí.

- [ ] název aplikace,
- [ ] krátký popis,
- [ ] dlouhý popis,
- [ ] ikona 512×512,
- [ ] feature graphic 1024×500,
- [ ] aktuální screenshoty,
- [ ] CZ texty odpovídají aplikaci,
- [ ] případné EN texty odpovídají aplikaci,
- [ ] screenshoty neukazují staré UI nebo neexistující přepínače.

`GOOGLE-PLAY-TEXTY.md` je potřeba před použitím porovnat s aktuálním buildem; starší texty nejsou automaticky autoritativní.

## 9. Další Play Console formuláře

- [ ] App access – aplikace nemá login; odpovědět podle aktuálního formuláře,
- [ ] Ads – aplikace neobsahuje reklamy,
- [ ] Content rating,
- [ ] Target audience / děti,
- [ ] Data safety,
- [ ] Location permissions,
- [ ] Privacy policy,
- [ ] případné deklarace special permissions zobrazené Play Console pro konkrétní bundle.

## 10. Device release gate

Před veřejným releasem nestačí emulátor.

Povinně projít `DEVICE-TEST-MATRIX.md`, zejména:

- reboot s aktivním geofence,
- reboot s time reminderem,
- vypnutí telefonu přes due time + catch-up,
- force-stop a následné ruční spuštění,
- revoke location/background permission,
- revoke exact alarm access,
- vypnuté notifikace,
- battery saver/OEM optimalizace,
- timezone + DST,
- ZIP backup/import,
- minifikovaný release: serialization, TTS, receivers, Glance widget, backup/import a Maps/Play Services.

## 11. Production gate

Do produkce neposílat, dokud není splněno:

- [ ] finální release CI zelené,
- [ ] R8/minifikace ověřená,
- [ ] Samsung/One UI kritická matrix zelená,
- [ ] čistý Android kritická matrix zelená,
- [ ] closed testing splněný, pokud jej účet vyžaduje,
- [ ] všechny Play Console declarations bez otevřených problémů,
- [ ] privacy policy veřejná a konzistentní,
- [ ] store listing aktuální,
- [ ] správný podepsaný `.aab` s novým versionCode.
