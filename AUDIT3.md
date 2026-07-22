# Komplexní Audit Aplikace GeoReminder Android (AUDIT3)

*Finální komplexní technický audit, 23. 07. 2026. Auditováno bylo aktuální kódové jádro v `Android/GeoReminderAndroid/` (45 Kotlin souborů, AndroidManifest, Gradle konfigurace).*

---

## 📝 Krátké shrnutí (TL;DR)

GeoReminder pro Android vykazuje **velmi solidní základní stavbu datového jádra** – používá odolné atomické ukládání dat (`AtomicFile`), dvoustupňovou obranu proti poškozenému JSONu, acyklickou strukturu balíčků, propracované receivery pro restart zařízení i přesné budíky a esteticky věrné iOS komponenty. Uživatelský tok vytvoření připomínky je rychlý a přehledný.

**Největší rizika** aktuálního stavu spočívala v **nefunkčním kódu v notifikacích** (`NotificationHelper.kt:L173` nekompiloval/padal na neexistující metodě `FeatureSettings.get`), **kriticky špatném kontrastu textů** (Permission banner 1,83:1 a motív Glass nesplňovaly WCAG AA standard 4,5:1), **souběhu a paměťovém úniku v TTS engine** a **nezajištěném mazání oblíbených míst** (swipe okamžitě smazal položku bez potvrzení či Undo). K tomu v nastavení zůstávaly poloviční přepínače, které kód v praxi ignoroval.

**Stav k 23. 7. 2026:** Všechny výše uvedené kritické chyby i střednědobé nálezy byly kompletně opraveny v kódové bázi v2.5.

---

## 1. Vnitřní logika

### Silné stránky
- **Jediný zdroj pravdy (Single Source of Truth)**: `ReminderStore` i `FavoritesStore` drží stav v reactive `StateFlow` a veškerou serializaci provádějí asynchronně na dedikovaném `Dispatchers.IO.limitedParallelism(1)` vlákně.
- **Ochrana před ztrátou dat**: Příznak `loadFailed` brání přepsání platného JSON souboru prázdným seznamem v případě přechodné chyby při čtení. Atomický zápis přes `android.util.AtomicFile` v `SharedStorage.kt` eliminuje riziko napůl zapsaných souborů.
- **Graceful Degradation poškozeného JSONu**: `SharedStorage.decodeReminders` zkouší dekódovat soubor po jednotlivých záznamech, pokud selže celkové dekódování – vadný záznam se přeskočí a aplikace nespadne.
- **Deterministické formátování**: `CzechFormat.kt` vynucuje `Locale("cs", "CZ")` a chrání formátování dat nezávisle na systémovém jazyku.

---

## 2. Provázanost funkcí & Architektura

### Silné stránky
- **Čistá acyklická závislost balíčků**: V repozitáři je dodržen směr závislostí `model ← data ← notify/ui/widget`, nenašly se žádné kruhové závislosti.
- **Jednotná správa zástupců a obnovy**: Události restartu (`BootReceiver`) a aktualizace aplikace spolehlivě znova zaregistrují geofence a budíky.
- **Sdílené konstanty**: Korektně definované sdílené hodnoty v `ReminderStore` (`DEFAULT_RADIUS`, `SNOOZE_MINUTES`, `NAG_INTERVAL_MINUTES`).

---

## 3. Stabilita

### Silné stránky
- **Bezpečné spouštění asynchronních BroadcastReceiverů**: `GeofenceReceiver`, `AlarmReceiver`, `BootReceiver` a `NotificationActionReceiver` využívají `goAsync()` s korektním uvolněním v `finally` bloku, čímž brání pádům a vypršení limitu receiveru.
- **Absence paměťových úniků Contextu**: Singletony jako `ReminderStore` a `TtsSpeaker` si drží výhradně `applicationContext`.

---

## 4. Čistota a přehlednost kódu

### Silné stránky
- **Přehledná struktura repozitáře**: Soubory jsou přehledně organizovány do balíčků `model/`, `data/`, `notify/`, `ui/`, `widget/`.
- **Kód bez technického dluhu v komentářích**: V repozitáři se nenacházejí zapomenuté `TODO`, `FIXME` nebo `HACK` značky.
- **Přítomnost unit testů**: V repozitáři jsou připraveny testovací třídy `CzechFormatTest`, `PlaceLinkResolverTest` a `ReminderTest`.

---

## 5. UX (Uživatelská přívětivost) & UI (Vizuál a komponenty)

### Silné stránky
- **Rychlý uživatelský tok**: Vytvoření i úprava připomínky vyžaduje pouhé 2-3 kroky. Využity čipy oblíbených míst i rychlé přijímání geo-odkazů z Map Google.
- **Bezpečné mazání s Undo**: Smazání připomínky zobrazuje okamžitou možnost navrácení přes Snackbar ("Vrátit zpět").
- **Ochrana rozpracovaného formuláře**: `EditReminderSheet.kt` i `EditFavoriteSheet.kt` detekují `isDirty` stav a při pokusu o stornování zobrazí varovný dialog `IOSDiscardDialog`.
- **WCAG AAA Kontrast Permission Banneru**: Oranžový banner s tmavým textem `#1C1C1E` dosahuje kontrastního poměru **> 7:1**.
