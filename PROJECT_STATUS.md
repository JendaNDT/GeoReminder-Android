# GeoReminder Android – Project Status
*Naposled aktualizováno: 23. 07. 2026 (v2.7 – po kompletní CZ/EN lokalizaci a přepínači jazyků)*

> **Poznámka k stavu:** Tento soubor byl 23. 7. plně sesynchronizován s realitou kódové báze v `Android/GeoReminderAndroid/` po dokončení všech oprav z auditu `AUDIT3.md` a realizaci plné CZ/EN lokalizace.

## 🎯 Co to je
Nativní Android verze GeoReminderu – připomínky vázané na místo i čas, vzhled 1:1 podle iOS předlohy (`design-podklady/DESIGN_SPEC.md`).
Stack: Kotlin + Jetpack Compose, Google Maps (Compose), GeofencingClient, AlarmManager (přesné budíky), Glance widget, App Shortcuts, JSON úložiště formátově kompatibilní s iOS. Minimum: Android 8 (API 26), target 35. Jeden modul.
**Verze: 2.7, versionCode 19.**

## ✅ Co je hotové a ověřené (v2.7 – AUDIT3 + Lokalizace stav)
- **Odolné a asynchronní úložiště:** Atomický zápis (`AtomicFile`), čtení `reminders.json` i `favorites.json` po jednotlivých záznamech s obranou `loadFailed` (přechodná chyba neshodí ani nepřepíše platný soubor). Čtení disku v `reload()` spouštěno asynchronně na `Dispatchers.IO.limitedParallelism(1)` mimo Main thread (UI se nezasekává).
- **Spolehlivé doručení & obnova:** Geofence (příjezd/odjezd, 50–1000 m, opakování) + časové připomínky (jednorázové/denní/týdenní) přes přesné budíky. Catch-up zmeškaných budíků, obnova po restartu telefonu (`BootReceiver`), zamknutá evidence `firedGeofenceIds`/`firedAlarmIds` proti souběhu a opakovanému střílení.
- **Robustní notifikace & TTS:** 3 notifikační kanály (Tiché/Výchozí/Naléhavé s dožadováním à 5 min), akce Hotovo/Odložit o hodinu/Zítra ráno/Navigovat. Vyřešen souběh v `TtsSpeaker` a doplněno uvolnění zdrojů (`shutdown()`). Funkční seskupování notifikací na stejném místě (`FeatureSettings.groupByPlace.value`).
- **UI & WCAG AA/AAA kontrast:** Vlastní iOS-look komponenty, 4 vyladěná témata (Světlé, Tmavé, Neutrální, Glass). Oranžový Permission banner s tmavým textem `#1C1C1E` splňuje kontrast **> 7:1** (WCAG AAA). Vyřešen kontrast štítků v tématech.
- **Bezpečné zacházení s daty a přílohami:**
  - Mazání oblíbených míst swipem vyžaduje potvrdzovací dialog (`IOSConfirmDialog`).
  - Formulář `EditFavoriteSheet` i `EditReminderSheet` detekují `isDirty`, odchytávají gesto Zpět (`BackHandler`) a chrání neuložená data přes `IOSDiscardDialog`.
  - Přílohy (foto/PDF do 10 MB) bezpečně kopírovány do `attachments/` bez rizika vznikajících 0 B souborů. Při zrušení formuláře nebo odebrání přílohy se fyzický soubor ihned smaže z disku.
  - Import záloh sanituje souřadnice (NaN check), limituje poloměr a deduplikuje záznamy podle ID.
- **Vyčištěná nastavení (bez fantomů):** Nefunkční volby zrušeny, v Nastavení zůstávají plně zapojené funkce (TTS nahlas, čtení celého textu, seskupení na místě, kalendář, JSON export/import s vysvětlením).
- **Přístupnost (TalkBack):** Doplněná tlačítka s textovými popisky (např. schválení kalendáře) a `contentDescription` pro kategoriové ikony i lupy vyhledávání.

## 📁 Kde co je
- **GitHub:** https://github.com/JendaNDT/GeoReminder-Android (`main`) – zdrojový kód + dokumentace. Tajnosti (mapový klíč, keystore) jsou mimo git (`.gitignore`), žijí v pracovní kopii na Macu.
- **Mac:** `/Users/jenda/Desktop/GeoReminder Android/` – `Android/GeoReminderAndroid/` = zdrojový kód + dokumentace; `AUDIT3.md` v kořeni.
- `NAVOD-INSTALACE.md` – postup instalace APK do telefonu; `GOOGLE-PLAY-CHECKLIST.md` – návod k vydání.

## 🏗️ Klíčová rozhodnutí
- **Výběr místa na mapě = celoobrazovkové okno (Dialog)**, ne sheet (gesto posunu mapy se nebije se zavíráním sheetu).
- **Hledání míst: Photon** (photon.komoot.io) primárně (zdarma, bez klíče); vestavěný `Geocoder` jako záloha.
- **Zálohování data JSON:** zjednodušený textový export/import připomínek a oblíbených s upozorněním (fyzické soubory příloh se nebalí).
- **Fantomové přepínače:** nefunkční volby (`adaptivePowerSaver`, `defaultRadius`, `defaultAlertStyle`) z kódu i z rozhraní odstraněny pro zachování poctivosti k uživateli.
- **minSdk 26, targetSdk 35, versionCode 17, versionName 2.5.**
