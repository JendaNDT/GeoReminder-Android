# GeoReminder Android – kompletní implementační plán oprav

**Datum auditu/plánu:** 7. 9. 2026  
**Výchozí stav:** `main`, aplikace v2.7 / versionCode 19  
**Cíl:** zvýšit spolehlivost doručování připomínek, odstranit nalezené funkční chyby, zabezpečit data a připravit aplikaci na veřejné vydání a target API 36.

> **Stav realizace k 7. 9. 2026:** Etapy 1–9 jsou implementované na stabilizační větvi a pokryté automatickým buildem/testy. PR zůstává draft do dokončení reálných device testů. Etapy 10–12 zbývají.

---

## 0. Pravidla realizace

Tento plán je záměrně seřazen podle závislostí. Kritické jádro se musí opravit dřív než UX a nové pohodlné funkce.

### Zásady

1. Implementovat etapy postupně v pořadí níže.
2. Po každé etapě musí aplikace zůstat sestavitelná a testovatelná.
3. Každá oprava kritické logiky musí dostat automatický test.
4. Nezavádět nové uživatelské funkce, dokud nejsou dokončené etapy 1–6.
5. Nechat všechny změny idempotentní: opakovaný `resync`, restart telefonu nebo opakované volání receiveru nesmí vytvořit duplicitní alarm ani duplicitní notifikaci.
6. Stav doručování nesmí být odvozen jen z UI paměti. Vše, co musí přežít restart procesu/telefonu, musí mít perzistentní zdroj pravdy.
7. Všechny chyby, které mohou způsobit nedoručení připomínky, musí být viditelné uživateli v diagnostice.

### Doporučený postup verzování

- Opravy P0/P1 realizovat jako jeden stabilizační milník, např. **v2.8**.
- API 36 + release hardening lze dokončit jako **v2.9**.
- Diagnostiku, lokalizaci a UX úklid lze případně vydat jako **v3.0**, pokud by rozsah příliš narostl.

---

# ETAPA 1 – Opravit načítání dat a závody při studeném startu [P0] ✅ IMPLEMENTOVÁNO

## Problém

`ReminderStore.reload()` spouští čtení asynchronně a okamžitě se vrací. `BootReceiver`, `AlarmReceiver`, `GeofenceReceiver`, `NotificationActionReceiver` i `RootScreen` mohou následně pracovat se starým nebo prázdným `reminders.value`.

Nejhorší scénář: po restartu telefonu se `resyncAll()` provede nad prázdným seznamem a aktivní geofence/alarmy se neobnoví.

## Soubory

- `data/ReminderStore.kt`
- `notify/BootReceiver.kt`
- `notify/AlarmReceiver.kt`
- `notify/GeofenceReceiver.kt`
- `notify/NotificationActionReceiver.kt`
- `ui/RootScreen.kt`

## Implementace

### 1.1 Zavést čekatelné načtení

Přidat do `ReminderStore` jednu jasnou funkci, například:

- `suspend fun reloadAndWait(): LoadResult`

nebo ekvivalentní synchronizované API.

Funkce musí:

- načíst data na `Dispatchers.IO`,
- aktualizovat `StateFlow`,
- vrátit výsledek načtení,
- skončit až ve chvíli, kdy jsou data opravdu k dispozici.

### 1.2 Odstranit vzor `reload(); okamžitě pokračuj`

Receivery musí mít tok:

1. `goAsync()`
2. načíst data a čekat na dokončení
3. teprve potom najít reminder / udělat resync
4. `pending.finish()` ve `finally`

### 1.3 BootReceiver

Po bootu nebo aktualizaci aplikace:

1. načíst data,
2. ověřit výsledek,
3. teprve potom obnovit alarmy/geofence/snooze,
4. při chybě dat nic destruktivně nepřepisovat.

### 1.4 RootScreen / návrat do popředí

Při `ON_RESUME` nesmí běžet `reload()` a `resyncAll()` paralelně bez vazby.

Použít jediný koordinovaný tok:

- načíst,
- potom resync,
- potom refresh stavu oprávnění/polohy.

## Testy

- studený start procesu + `BootReceiver`
- studený start procesu + `AlarmReceiver`
- studený start procesu + `GeofenceReceiver`
- studený start procesu + akce „Hotovo“ z notifikace
- `ON_RESUME` po změně dat mimo UI

## Hotovo když

- restart telefonu vždy obnoví všechny aktivní spouštěče,
- receiver nikdy nepracuje s prázdným seznamem pouze proto, že IO ještě nedoběhlo,
- opakovaný resync nevytváří duplicity.

---

# ETAPA 2 – Centralizovat stav plánování a idempotentní resync [P0/P1] ✅ IMPLEMENTOVÁNO

## Cíl

`ReminderScheduler` má být jediný vlastník systémového stavu alarmů/geofence/snooze. Všechny ostatní vrstvy jen sdělují požadovaný stav.

## Soubory

- `notify/ReminderScheduler.kt`
- `data/ReminderStore.kt`
- případně nový model `SchedulingState.kt`

## Implementace

### 2.1 Zavést jasný kontrakt scheduleru

Preferované API:

- `schedule(reminder)`
- `cancel(reminderId)`
- `resync(allReminders)`
- `snoozeUntil(reminderId, time)`
- `resumeAfterSnooze(reminderId)`
- `markTriggered(...)`

UI ani receiver nemá přímo skládat vlastní náhradní plánovací logiku.

### 2.2 Perzistentní stav spouštěče

Zvážit perzistentní interní stav pro každou připomínku:

- ACTIVE
- SNOOZED_UNTIL
- FIRED_ONE_TIME
- DONE
- REGISTRATION_FAILED

Nemusí být součástí veřejného exportního modelu `Reminder`, může jít o interní úložiště podle ID.

### 2.3 Zamezit kolizím PendingIntentů

Prověřit `hashCode()` requestCode schéma.

Doporučeno:

- centrální generování requestCode,
- oddělené namespace pro alarm / snooze / nag,
- případně mapování ID → stabilní interní integer.

Cíl: minimální riziko kolize dvou UUID hashů.

### 2.4 Evidence „fired“

`firedGeofenceIds` a `firedAlarmIds` ponechat jen pokud mají stále jasný účel po zavedení stavového modelu.

Preferovat jednu konzistentní evidenci místo více nezávislých značek.

## Testy

- 100 opakovaných resynců = stále jeden systémový spouštěč na reminder
- cancel → schedule
- editace reminderu → starý spouštěč pryč, nový aktivní
- označit Hotovo → všechny typy alarmů/geofence/nag/snooze zrušené

## Hotovo když

- plánovací stav je deterministický,
- žádná UI akce nemění systémový plán „bokem“.

---

# ETAPA 3 – Opravit časové připomínky a snooze [P0] ✅ IMPLEMENTOVÁNO

## 3.1 Opravit editaci opakovaných připomínek

### Problém

`EditReminderSheet` používá `existing?.dueDate?.coerceAtLeast(System.currentTimeMillis())` i pro DAILY/WEEKLY reminder.

Tím lze změnou názvu nechtěně změnit čas opakování.

### Oprava

- `TimeRepeat.NEVER`: starý čas lze při editaci řešit samostatně.
- `DAILY` / `WEEKLY`: zachovat původní `dueDate` beze změny.
- Nikdy neměnit uloženou hodinu jen proto, že datum leží v minulosti.

### Test

Denní reminder v 07:00, otevřen v 16:35, změněn pouze název → stále 07:00.

---

## 3.2 Opravit rychlou akci „Odložit na zítra ráno“

### Problém

Rychlá akce mění `dueDate`, místo aby používala snooze.

Dopad:

- location reminder se prakticky neodloží,
- opakovaný time reminder může změnit svůj pravidelný čas.

### Oprava

Akce musí použít jediný schedulerový mechanismus:

- `store.snoozeAt(reminder, nextMorningMillis())`

Původní reminder se nesmí přepisovat.

---

## 3.3 Snooze musí dočasně blokovat původní trigger

### Problém

U geo reminderu se snooze alarm vytvoří, ale původní geofence zůstává aktivní.

### Oprava

Při snooze:

1. zrušit `nag`,
2. označit reminder jako `SNOOZED_UNTIL`,
3. dočasně odregistrovat geofence / původní časový trigger,
4. naplánovat snooze alarm,
5. po skončení snooze doručit reminder,
6. podle typu reminderu obnovit původní opakovaný trigger.

Jednorázový reminder po snooze nesmí omylem přejít do permanentního opakování.

---

## 3.4 Opakování přes změnu časového pásma a DST

Ověřit `nextDaily()` a `nextWeekly()` pro:

- přechod letní → zimní čas,
- zimní → letní čas,
- změnu časového pásma,
- změnu systémového data/času.

Doplnit receiver/resync pro relevantní systémové změny času, pokud testy prokážou potřebu.

## Testy etapy

- NEVER / DAILY / WEEKLY
- více vybraných dnů týdne
- snooze time reminderu
- snooze geo reminderu
- reboot během snooze
- reboot po vypršení snooze
- DST
- změna timezone

## Hotovo když

- snooze nikdy nemění původní plán reminderu,
- reminder během snooze nemůže vystřelit původním triggerem.

---

# ETAPA 4 – Přesné alarmy, oprávnění a Android 16 / API 36 [P1] ✅ IMPLEMENTOVÁNO

> Externí podmínky byly znovu ověřeny 7. 9. 2026 podle Android Developers a Google Play Console Help.

## 4.1 Přejít na target API 36

Současnost:

- `compileSdk = 35`
- `targetSdk = 35`

Od 31. 8. 2026 musí nové mobilní aplikace a aktualizace na Google Play cílit na Android 16 / API 36 (případné prodloužení termínu je pouze dočasná výjimka).

### Oprava

- `compileSdk = 36`
- `targetSdk = 36`
- aktualizovat kompatibilní Android Gradle Plugin / knihovny podle potřeby,
- projít behavior changes Android 16,
- otestovat Android 8 (minSdk 26), Android 13, 14, 15 a 16.

---

## 4.2 Přehodnotit `USE_EXACT_ALARM`

Google Play omezuje `USE_EXACT_ALARM` převážně na alarm/timer/calendar use-cases. GeoReminder je reminder aplikace, proto je bezpečnější připravit variantu s:

- `SCHEDULE_EXACT_ALARM`

bez současného `USE_EXACT_ALARM`, pokud nebude potvrzená přijatelnost v Play Console.

### Implementace

- před exact alarmem vždy kontrolovat `AlarmManager.canScheduleExactAlarms()`;
- při nepovoleném stavu neukrývat problém;
- zobrazit banner „Přesné časové připomínky nejsou povoleny“;
- tlačítko otevře `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`;
- po návratu do appky stav znovu zkontrolovat;
- po udělení oprávnění provést resync všech time reminderů;
- implementovat reakci na `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` podle doporučení Androidu.

### Fallback

Pokud exact alarm není povolen:

- lze použít inexact fallback,
- UI ale musí jasně uvést, že reminder se může opozdit.

---

## 4.3 Opravit background location flow

Na Androidu 11+ se „Povolit vždy“ neuděluje běžným runtime dialogem. Uživatel musí do systémového Nastavení.

### Implementace

1. vyžádat foreground fine/coarse location,
2. vysvětlit, proč geofence potřebuje background location,
3. zobrazit lokalizovaný název systémové volby přes API, kde je to možné,
4. otevřít správné Settings UI,
5. po návratu zkontrolovat oprávnění,
6. teprve potom označit geo reminder jako plně aktivní.

`ReminderScheduler.addGeofence()` musí kontrolovat skutečné background oprávnění, ne jen fine location.

---

## 4.4 Battery optimization

Nežádat uživatele agresivně hned při onboardingu.

Doporučení:

- vysvětlit význam až při vytvoření první background připomínky,
- stav zobrazit v diagnostice,
- zachovat odkaz do systémového nastavení.

## Hotovo když

- aplikace projde API 36 buildem,
- uživatel vždy ví, když exact/background permission chybí,
- žádná kritická capability neselže potichu.

---

# ETAPA 5 – Geofence spolehlivost [P1] ✅ IMPLEMENTOVÁNO

## 5.1 Per-reminder stav registrace

Současný globální `LocationHolder.geofenceFailed` nestačí.

Jedna úspěšná registrace může zamaskovat jinou neúspěšnou.

### Zavést stav podle ID

Např.:

- ACTIVE
- FAILED_PERMISSION
- FAILED_LOCATION_DISABLED
- FAILED_TOO_MANY
- FAILED_SERVICE
- SNOOZED
- FIRED

UI pak může ukázat problém přímo u konkrétního reminderu.

---

## 5.2 Limit 100 geofence

Google Play Services má limit 100 aktivních geofence na aplikaci/uživatele.

### Implementace

- před registrací sledovat počet požadovaných aktivních geofence,
- při překročení limitu zabránit tichému selhání,
- uživateli vysvětlit, které remindery nejsou aktivní,
- diagnostika: `aktivní geofence X/100`.

---

## 5.3 Validace regionu

Před registrací kontrolovat:

- latitude -90..90,
- longitude -180..180,
- radius v podporovaném rozsahu aplikace,
- konečné hodnoty (ne NaN / Infinity).

Poškozený reminder se nesmí poslat do Google Play Services.

---

## 5.4 Geofence error logging

Ukládat poslední důvod selhání v bezpečném diagnostickém logu bez citlivé přesné historie polohy.

## Testy

- chybějící background permission
- vypnutá poloha v systému
- limit geofence
- poškozené souřadnice
- opakovaný resync
- reboot

---

# ETAPA 6 – Integrita dat, přílohy a zálohy [P0/P1] ✅ IMPLEMENTOVÁNO

## 6.1 Rozlišit poškozený JSON od legitimně prázdného seznamu

`decodeReminders()` nesmí při fatálním poškození skončit jako obyčejné `emptyList()`.

### Nový výsledek dekódování

Např.:

- `Success(list)`
- `Partial(list, skippedCount)`
- `Corrupted(error)`

### Chování

- `Success` → normální provoz
- `Partial` → zachovat načtené záznamy, vytvořit kopii původního souboru, varovat v diagnostice
- `Corrupted` → nikdy automaticky nepřepsat původní soubor

Doporučeno vytvořit interní záložní kopii typu `reminders.corrupt-<timestamp>.json`.

---

## 6.2 Zabezpečit mazání příloh

`deleteAttachment(path)` smí smazat pouze soubor uvnitř kanonického:

- `<filesDir>/attachments/`

### Povinné kontroly

- získat `canonicalFile`,
- ověřit, že je potomkem `attachmentsDir.canonicalFile`,
- odmítnout jiné cesty,
- ignorovat relativní traversal pokusy.

Totéž aplikovat při otevírání/importu příloh.

---

## 6.3 Import JSON musí sanitizovat `attachmentPath`

Současný JSON export fyzické přílohy neobsahuje.

Proto při importu obyčejného JSONu:

- `attachmentPath = null`

Nikdy nepřebírat absolutní cestu z cizího zařízení.

---

## 6.4 Automatická Android backup pravidla

Současná cloud/device transfer záloha obsahuje JSON, ale ne fyzické přílohy.

Rozhodnout jednu z variant:

### Varianta A – podporovat přílohy

Zahrnout adresář `attachments/` do backup/device transfer a po obnově ověřit existenci každé vazby.

### Varianta B – přílohy nezálohovat

Po obnově automaticky odstranit neexistující `attachmentPath`.

Preferovaná varianta pro uživatele: A.

---

## 6.5 Nový plnohodnotný export ZIP

Doporučené rozšíření zálohy:

```
georeminder-backup.zip
  backup.json
  attachments/
    <id>.jpg
    <id>.pdf
```

JSON nemá obsahovat absolutní systémové cesty, ale stabilní relativní identifikátor přílohy.

Zachovat import starého JSONu pro zpětnou kompatibilitu.

---

## 6.6 Dávkový import

Místo `add/update` pro každou položku:

1. načíst celý backup,
2. validovat,
3. deduplikovat,
4. vytvořit výsledný snapshot,
5. jeden atomický zápis,
6. jeden widget refresh,
7. jeden scheduler resync.

---

## 6.7 Opravit falešný úspěch exportu

Pokud `openOutputStream()` vrátí `null`, export musí vrátit `false`.

## Testy

- kompletně rozbitý JSON
- jeden rozbitý záznam mezi platnými
- import cesty `/data/.../jinysoubor`
- path traversal
- 0 B příloha
- >10 MB
- export/import 100+ reminderů
- import starého JSON formátu
- ZIP backup s přílohami

---

# ETAPA 7 – Notifikace, TTS a deep-linking [P1/P2] ✅ IMPLEMENTOVÁNO

## 7.1 Kliknutí na notifikaci otevře konkrétní reminder

Do content intentu přidat reminder ID.

`MainActivity`/UI musí umět:

- otevřít aplikaci,
- přepnout na seznam reminderů,
- otevřít konkrétní reminder/detail/editaci.

Stejný mechanismus lze znovu použít pro widget.

---

## 7.2 Akce z notifikace musí být idempotentní

Ověřit:

- dvojité klepnutí Hotovo,
- dvojité Snooze,
- akce po smazání reminderu,
- akce po označení Hotovo v jiném procesu.

Žádná z těchto situací nesmí vytvořit další alarm.

---

## 7.3 TTS inicializovat jen pokud je potřeba

V `speakIfEnabled()` nejprve zkontrolovat nastavení.

Pokud TTS init selže:

- zavřít/nechat zahodit vadný engine,
- vyčistit nebo rozumně omezit pending frontu,
- umožnit další inicializační pokus.

Doplnit `UtteranceProgressListener` pro lepší stavovou kontrolu.

---

## 7.4 Jazyk TTS

TTS nemá být natvrdo české při přepnutí aplikace do angličtiny.

Použít aktivní jazyk aplikace / vhodný locale fallback.

---

# ETAPA 8 – Kalendář, vyhledávání míst a widget [P2] ✅ IMPLEMENTOVÁNO

## 8.1 Kalendář na IO

`CalendarProvider` dotazy spouštět na `Dispatchers.IO`.

UI musí mít stav:

- loading
- success
- empty
- permission denied
- error

---

## 8.2 Použít CalendarContract.Instances

Pro příštích 30 dní načítat skutečné výskyty opakovaných událostí, ne pouze základní `Events` řádky.

---

## 8.3 Deduplikace importu kalendáře

Do reminderu nebo interní metadata vrstvy uložit zdrojový calendar event/instance identifikátor.

Již importovaná událost:

- nezobrazovat jako novou,
- nebo zobrazit stav „již importováno“.

---

## 8.4 Photon repository musí vracet typ chyby

Místo `emptyList()` při všem:

- Success(results)
- NoResults
- NetworkError
- ServerError(code)
- ParseError

UI pak správně rozliší „nic nenalezeno“ a „není internet“.

---

## 8.5 Validovat souřadnice z Google Maps odkazu

`PlaceLinkResolver` musí po parsování zkontrolovat rozsah latitude/longitude.

---

## 8.6 Widget řadit skutečně podle významu

Dnes se aktivní položky řadí podle `createdAt`.

Navržené pořadí:

1. časové remindery podle nejbližšího budoucího výskytu,
2. geo remindery podle vzdálenosti, pokud existuje čerstvá poloha,
3. jinak podle vytvoření.

Minimální varianta: přejmenovat widget, pokud má zůstat řazení podle vytvoření.

### Stav implementace Etapy 8

- kalendář používá `CalendarContract.Instances` a dotaz běží na `Dispatchers.IO`,
- UI rozlišuje loading / content / empty / permission denied / error,
- `null` cursor provideru je chyba, ne falešně prázdný kalendář,
- každý konkrétní výskyt dostává stabilní `calendarSourceKey = eventId:beginMillis`,
- již importované instance jsou označené a nelze je znovu vybrat,
- import vybraných instancí proběhne dávkově jedním atomickým snapshotem,
- Photon vrací `Success / NoResults / NetworkError / ServerError / ParseError`,
- Android Geocoder zůstává fallback, ale neúspěch už nemaskuje skutečný Photon stav,
- souřadnice z Photon, Geocoderu i sdílených mapových odkazů procházejí validačním rozsahem,
- widget řadí časové remindery podle příštího výskytu a geo podle čerstvé polohy; starou polohu ignoruje,
- přidány/rozšířeny `CalendarImporterTest`, `PlaceLinkResolverTest` a `WidgetOrderingTest`,
- aplikační head Etapy 8 `267d581eb3c064c25cb63f012405da2727db6dc0` prošel GitHub Actions run #68 (`34128384063`): unit testy zelené + debug APK sestavené na API 36.

---

# ETAPA 9 – Lokalizace a konzistence UI [P2] ✅ IMPLEMENTOVÁNO

## 9.1 Přestat používat vlastní globální přepis Locale

Nahradit `resources.updateConfiguration()` standardním Android per-app language řešením (AppCompat / LocaleManager podle podporované architektury).

Volba „Podle systému“ musí vždy obnovit skutečný systémový jazyk.

---

## 9.2 Přesunout všechny texty z Kotlinu do resources

Projít minimálně:

- ReminderListScreen
- SettingsSheet
- EditReminderSheet
- LocationPickerSheet
- MapOverviewScreen
- CalendarImportSheet
- notifikační texty
- widget
- diagnostiku

Doplnit CZ + EN.

Aplikace nesmí tvrdit „plná CZ/EN lokalizace“, dokud nejsou všechny uživatelské texty v resources.

---

## 9.3 Verze aplikace

Odstranit ručně napsané `v2.5` z Nastavení.

Číst `versionName` z `BuildConfig`/PackageInfo.

---

## 9.4 Ochrana rozpracovaného formuláře při zavření sheetu

`BackHandler` nestačí, pokud parent `ModalBottomSheet.onDismissRequest` rovnou nastaví `editingReminder = null`.

Vytvořit jednotný kontrakt:

- EditReminderSheet rozhoduje, zda lze zavřít,
- gesto mimo sheet / swipe / Back používají stejnou `requestClose()` logiku,
- při změnách se vždy zobrazí discard dialog.

Stejné chování na seznamu i mapě.

---

## 9.5 Přílohy – správné typy souborů

UI slibuje fotku/PDF, takže nepoužívat `*/*` bez následné validace.

Povolit pouze podporované MIME typy:

- `image/jpeg`
- `image/png`
- `application/pdf`

Případně další obrazové formáty až po explicitní podpoře.

### Stav implementace Etapy 9

- `MainActivity` přešla na `AppCompatActivity` a jazyk se řídí `AppCompatDelegate.setApplicationLocales()`; starý globální `resources.updateConfiguration()` byl odstraněn,
- `android:localeConfig` deklaruje `cs-CZ` a `en-US`; AppCompat automaticky ukládá locale na Androidu 12 a starším,
- stará preference `SYSTEM/CS/EN` se jednorázově migruje, ale dál už není paralelním zdrojem pravdy,
- `SYSTEM` je skutečný prázdný app-locale override; nepodporovaný systémový jazyk používá české default resources, anglický systém používá `values-en`,
- pro receiver/widget/TTS se používá `ContextCompat.getContextForLanguage()`, takže ručně zvolený jazyk funguje i při cold-startu bez Activity,
- vznikl centralizovaný `ReminderText`; model `Reminder` a enumy už neobsahují natvrdo české uživatelské labely/subtitle,
- hlavní obrazovky, kalendář, picker míst, oblíbená místa, notifikace, TTS, widget, geofence stavy a accessibility texty používají CZ/EN resources,
- názvy a popisy notification channelů se po změně jazyka znovu registrují v aktivním locale bez resetu uživatelských channel nastavení,
- Nastavení čte `versionName` z nainstalovaného balíčku místo ručně napsané `v2.5`,
- nový společný `ReminderEditorModal` sjednocuje Back / swipe-down / tap outside dismiss a při dirty formuláři vždy vyvolá stejný discard dialog; používá se ze seznamu i mapy,
- picker příloh nabízí jen JPEG/PNG/PDF a `AttachmentHelper` stejné MIME typy znovu validuje v datové vrstvě,
- přidán `AttachmentPolicyTest`, locale-aware regresní testy formátování a `LocalizationResourcesTest`, který vyžaduje shodnou množinu CZ/EN string klíčů,
- aplikační head Etapy 9 `8c98a5695022755a752a1d70ea6a91384654864d` prošel GitHub Actions run #123 (`34137734616`): **unit testy zelené + debug APK sestavené na API 36**.

---

# ETAPA 10 – Automatické testy kritické cesty [P0/P1]

Toto není volitelný kosmetický bod. Reminder aplikace bez testů doručování je sázka na to, že Android bude mít dobrou náladu.

## 10.1 Unit testy

Doplnit minimálně:

### Scheduler math

- `nextDaily`
- `nextWeekly`
- pondělí–neděle
- více weekdays
- DST
- přechod roku

### Data

- validní JSON
- partial corrupted JSON
- fatal corrupted JSON
- kompatibilita AppleDateSerializer
- sanitace importu

### Backup

- starý JSON
- nový ZIP
- deduplikace
- neplatná attachment path

### Resolver

- valid/invalid geo URI
- short links
- out-of-range coordinates

---

## 10.2 Instrumentation / Android testy

Doplnit scénáře:

- vytvořit time reminder → zaregistrován alarm
- editovat → starý alarm zrušen
- Hotovo → vše zrušeno
- Snooze → původní trigger pozastaven
- snooze po rebootu
- receiver při cold startu
- background permission denied/granted
- notification permission denied
- exact alarm denied/granted

---

## 10.3 Manuální device matrix

Minimálně:

- Samsung / One UI (hlavní reálné zařízení)
- Pixel / čistý Android nebo emulator
- API 26
- API 33
- API 34
- API 35
- API 36

### Povinné manuální scénáře

1. restart telefonu s aktivním geofence
2. restart telefonu s časovým reminderem za 5 minut
3. vypnout telefon přes termín a znovu zapnout
4. kill aplikace systémem
5. force stop – dokumentovat, že Android po force-stop omezuje behavior do ručního spuštění
6. revoke location permission
7. revoke exact alarm access
8. vypnout notifikace
9. zapnout battery saver
10. změnit timezone
11. DST test přes emulator

---

# ETAPA 11 – Diagnostika spolehlivosti [P2, vysoká hodnota]

Přidat obrazovku **Diagnostika připomínek**.

## Zobrazit

- Notifikace: OK / problém
- Fine location: OK / problém
- Background location: OK / problém
- Systémová poloha zapnutá: OK / problém
- Exact alarms: OK / nepovoleno
- Battery optimization: OK / omezeno
- Aktivní geo remindery: X
- Registrované/neúspěšné geofence: X / Y
- Limit: X / 100
- Aktivní time remindery: X
- Nejbližší časový alarm: datum + čas
- Počet snoozed reminderů
- Poslední úspěšný `resync`
- Poslední chyba registrace
- Stav datového souboru: OK / partial recovery / corrupted

## Akce

- „Opravit oprávnění“
- „Znovu synchronizovat připomínky“
- „Testovací připomínka za 1 minutu“
- „Zkopírovat diagnostický přehled“ bez přesných GPS souřadnic a osobních názvů reminderů

## Interní diagnostika

Udržovat malý ring-buffer technických událostí, například posledních 50:

- RESYNC_OK
- RESYNC_FAILED
- GEOFENCE_REGISTER_OK/FAIL
- ALARM_SCHEDULED
- ALARM_FIRED
- SNOOZE_SET
- DATA_PARTIAL_RECOVERY

Neukládat historii přesné polohy uživatele.

---

# ETAPA 12 – Release hardening a dokumentace [P1/P2]

## 12.1 Zapnout lint

Současné:

- `checkReleaseBuilds = false`
- `abortOnError = false`

Po opravách:

- zapnout release lint,
- nechat build selhat na skutečně kritických problémech,
- jednotlivé false-positive výjimky řešit konkrétně, ne globálním vypnutím.

---

## 12.2 R8/minifikace

Nejdřív vytvořit testovaný release bez minifikace.

Potom:

1. zapnout R8,
2. otestovat serialization modely,
3. TTS,
4. receivery,
5. Glance widget,
6. backup/import,
7. Google Maps/Play Services.

Pokud bude vše stabilní, minifikaci ponechat.

---

## 12.3 CI na GitHub Actions

Přidat pipeline:

- Gradle build
- unit tests
- lint
- případně debug APK artifact

Každý push/PR do `main` musí projít minimálně build + unit tests.

---

## 12.4 Sjednotit dokumentaci

Aktualizovat:

- `README.md`
- `PROJECT_STATUS.md`
- `GOOGLE-PLAY-CHECKLIST.md`
- staré `AUDIT*.md` označit jako historické, ne jako aktuální stav
- staré implementační plány označit stavem Hotovo / Nahrazeno / Zbývá

`PROJECT_STATUS.md` nesmí současně obsahovat různé versionCode/versionName.

---

## 12.5 Google Play kontrola před releasem

Před odesláním:

- target API 36
- Data safety – přesná/background location
- deklarace použití location permissions
- kontrola exact alarm permission policy
- privacy policy odpovídá skutečnému chování
- screenshoty a texty nepopisují neexistující funkce
- test internal/closed track

---

# Doporučené pořadí commitů

1. `fix: make reminder loading awaitable`
2. `fix: make receivers cold-start safe`
3. `refactor: centralize reminder scheduling state`
4. `fix: preserve repeating reminder time on edit`
5. `fix: unify snooze behavior for time and location reminders`
6. `fix: harden exact alarm and background location permission flow`
7. `build: target Android 16 API 36`
8. `fix: track geofence registration per reminder`
9. `fix: harden corrupted data recovery`
10. `fix: secure attachment paths and backup import`
11. `feat: add attachment-aware backup format`
12. `fix: notification deep links and TTS lifecycle`
13. `fix: calendar instances and import deduplication`
14. `fix: expose Photon network errors`
15. `fix: sort widget by upcoming reminders`
16. `refactor: migrate all UI text to Android resources`
17. `fix: protect dirty editor from sheet dismiss`
18. `test: add scheduler and storage regression suite`
19. `test: add receiver and permission instrumentation tests`
20. `feat: add reminder diagnostics screen`
21. `ci: enable lint tests and GitHub Actions`
22. `docs: synchronize project status and release checklist`

---

# Milníky

## Milník A – „Připomínka musí přijít“

Etapy 1–5.

Po tomto milníku musí být spolehlivé:

- reboot,
- cold start,
- geofence,
- time alarm,
- snooze,
- exact alarm permissions,
- background location.

**Bez dokončení Milníku A nevydávat veřejnou verzi.**

---

## Milník B – „Nemůžeme přijít o data“

Etapa 6 + příslušné testy z etapy 10.

Po tomto milníku:

- poškozený JSON nezničí platná data,
- cizí backup nemůže mazat soubory mimo attachments,
- backup/restore má definované chování pro přílohy.

---

## Milník C – „Produkční Android aplikace“

Etapy 7–10 + API 36.

Po tomto milníku:

- build/test/lint je opakovatelný,
- kritické regrese zachytí CI,
- app je připravena na closed testing.

---

## Milník D – „Uživatel pozná, co je špatně“

Etapy 11–12.

Po tomto milníku:

- uživatel dostane konkrétní vysvětlení problému,
- vývojář dostane bezpečný diagnostický přehled,
- dokumentace odpovídá skutečnosti.

---

# Definition of Done pro veřejné vydání

Veřejný release je připravený pouze pokud platí všechno:

- [x] `targetSdk 36`
- [ ] build release projde
- [ ] lint projde bez ignorování celé kontroly
- [x] všechny současné unit testy zelené
- [ ] všechny kritické instrumentation testy zelené
- [ ] restart telefonu obnoví alarmy i geofence – ověřit na zařízení
- [x] cold-start receivery používají načtená data
- [x] snooze blokuje původní trigger
- [x] editace opakovaného reminderu nemění čas bez zásahu uživatele
- [x] exact alarm stav je viditelný a opravitelný
- [x] background location stav je viditelný a opravitelný
- [x] geofence failure je per-reminder, ne jen globální boolean
- [x] poškozený JSON se automaticky nepřepíše bez recovery kopie
- [x] attachment path je sandboxovaná
- [x] backup má definované chování příloh
- [x] kliknutí na notifikaci otevře správný reminder
- [x] calendar import neduplikuje stejné instance
- [x] offline hledání místa nehlásí falešně „nic nenalezeno“
- [x] všechny podporované texty jsou v CZ/EN resources
- [x] číslo verze v UI se bere z buildu
- [ ] diagnostická obrazovka ukazuje stav kritických systémových oprávnění
- [ ] `PROJECT_STATUS.md` odpovídá skutečnému buildu
- [ ] closed-test verze byla ověřena minimálně na Samsung/One UI a čistém Androidu

---

# Co naopak zatím nepřidávat

Dokud není výše uvedené hotové, nepřidávat:

- cloud účet/synchronizaci,
- nové typy reminderů,
- AI funkce,
- další mapové poskytovatele,
- složité kategorie/automatizace,
- další systémová oprávnění.

GeoReminder už má funkcí dost. Prioritou je, aby existující funkce byly předvídatelné, testované a spolehlivé.

---

# Výsledná priorita

### P0 – okamžitě

- cold-start / reload race ✅
- editace repeating time ✅
- špatná quick-snooze akce ✅
- skutečný snooze stav ✅
- bezpečnost attachment path ✅

### P1 – před veřejným releasem

- API 36 ✅
- exact alarm permission flow ✅
- background location flow ✅
- per-reminder geofence status ✅
- corrupted JSON recovery ✅
- backup/import hardening ✅
- testy kritické cesty – částečně, instrumentation zbývá
- lint/CI – CI build+unit hotovo, lint zbývá

### P2 – po stabilizaci jádra

- diagnostika
- calendar Instances + dedup ✅
- Photon error states ✅
- widget pořadí ✅
- TTS lifecycle ✅
- deep linking ✅
- kompletní lokalizace ✅
- dokumentační úklid

---

**Hlavní princip celého plánu:** GeoReminder není aplikace, u které stačí „většinou funguje“. Její hlavní produktová vlastnost je důvěra. Uživatel musí mít jistotu, že aktivní připomínka je skutečně aktivní, a pokud není, aplikace mu musí přesně říct proč.
