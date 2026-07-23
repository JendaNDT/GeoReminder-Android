# GeoReminder Android – Project Status
*Naposled aktualizováno: 23. 07. 2026 (v2.8 – po opravách AUDIT4)*

> **Poznámka k stavu:** Kritické nálezy z `AUDIT4.md` jsou opravené. Zbývající rizika jsou především terénní ověření alarmů/geofence po restartu a omezeních různých výrobců, instrumentační process-death testy a volitelný vizuální polish.

## 🎯 Co to je
Nativní Android verze GeoReminderu – připomínky vázané na místo i čas, vzhled 1:1 podle iOS předlohy (`design-podklady/DESIGN_SPEC.md`).
Stack: Kotlin + Jetpack Compose, Google Maps (Compose), GeofencingClient, AlarmManager (přesné budíky), Glance widget, App Shortcuts, JSON úložiště formátově kompatibilní s iOS. Minimum: Android 8 (API 26), target 36. Jeden modul.
**Verze: 2.8, versionCode 20.**

## ✅ Co je hotové a ověřené (v2.8 – stav po AUDIT4)
- **Odolné a asynchronní úložiště:** Atomický zápis (`AtomicFile`), čtení `reminders.json` i `favorites.json` po jednotlivých záznamech s obranou `loadFailed` (přechodná chyba neshodí ani nepřepíše platný soubor). Čtení disku v `reload()` spouštěno asynchronně na `Dispatchers.IO.limitedParallelism(1)` mimo Main thread (UI se nezasekává).
- **Spolehlivé doručení & obnova:** Všechny receivery nejdřív awaitují data z disku; Hotovo čeká na durabilní zápis. Jednorázové alarmy/geofence mají atomickou in-process evidenci doručení a nejsou označeny jako doručené, pokud systém notifikaci odmítl. Catch-up i obnova po restartu zůstávají zapojené.
- **Robustní notifikace & TTS:** 3 notifikační kanály (Tiché/Výchozí/Naléhavé s dožadováním à 5 min), akce Hotovo/Odložit o hodinu/Zítra ráno. Vyřešen souběh v `TtsSpeaker`, doplněno uvolnění zdrojů a seskupování na stejném místě (`FeatureSettings.groupByPlace.value`).
- **UI & WCAG AA/AAA kontrast:** Vlastní iOS-look komponenty, 4 vyladěná témata (Světlé, Tmavé, Neutrální, Glass). Oranžový Permission banner s tmavým textem `#1C1C1E` splňuje kontrast **> 7:1** (WCAG AAA). Vyřešen kontrast štítků v tématech.
- **Bezpečné zacházení s daty a přílohami:**
  - Mazání oblíbených míst swipem vyžaduje potvrdzovací dialog (`IOSConfirmDialog`).
  - Formulář `EditFavoriteSheet` i `EditReminderSheet` detekují `isDirty`, odchytávají gesto Zpět (`BackHandler`) a chrání neuložená data přes `IOSDiscardDialog`.
  - Přílohy (foto/PDF do 10 MB) bezpečně kopírovány do `attachments/` bez rizika vznikajících 0 B souborů. Při zrušení formuláře nebo odebrání přílohy se fyzický soubor ihned smaže z disku.
  - Import záloh sanituje souřadnice (NaN check), limituje poloměr a deduplikuje záznamy podle ID.
- **Vyčištěná nastavení (bez fantomů):** Nefunkční volby zrušeny, v Nastavení zůstávají plně zapojené funkce (TTS nahlas, čtení celého textu, seskupení na místě, kalendář, JSON export/import s vysvětlením).
- **Přístupnost (TalkBack):** Doplněná tlačítka s textovými popisky (např. schválení kalendáře) a `contentDescription` pro kategoriové ikony i lupy vyhledávání.
- **Ověření:** 19/19 JVM unit testů, debug i release lint bez chyb, debug APK i minifikovaný release AAB. Emulator Android 16 ověřil onboarding Skip, plné přepnutí CZ/EN, ochranu rozepsaného formuláře, uložení reminderu a načtení po force-stop. CI opakuje test/lint/bundle gate na GitHubu.

## 📁 Kde co je
- **GitHub:** https://github.com/JendaNDT/GeoReminder-Android (`main`) – zdrojový kód + dokumentace. Tajnosti (mapový klíč, keystore) jsou mimo git (`.gitignore`), žijí v pracovní kopii na Macu.
- **Mac:** `/Users/jenda/Desktop/GeoReminder Android/` – `Android/GeoReminderAndroid/` = zdrojový kód + dokumentace; `AUDIT3.md` v kořeni.
- `NAVOD-INSTALACE.md` – postup instalace APK do telefonu; `GOOGLE-PLAY-CHECKLIST.md` – návod k vydání.

## 🏗️ Klíčová rozhodnutí
- **Výběr místa na mapě = celoobrazovkové okno (Dialog)**, ne sheet (gesto posunu mapy se nebije se zavíráním sheetu).
- **Hledání míst: Photon** (photon.komoot.io) primárně (zdarma, bez klíče); vestavěný `Geocoder` jako záloha.
- **Zálohování data JSON:** zjednodušený textový export/import připomínek a oblíbených s upozorněním (fyzické soubory příloh se nebalí).
- **Fantomové přepínače:** nefunkční volby (`adaptivePowerSaver`, `defaultRadius`, `defaultAlertStyle`) z kódu i z rozhraní odstraněny pro zachování poctivosti k uživateli.
- **minSdk 26, compile/targetSdk 36, versionCode 20, versionName 2.8.**
