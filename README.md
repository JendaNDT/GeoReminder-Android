# GeoReminder (Android)

Nativní Android verze GeoReminderu – **„Připomeň mi X, až budu u Y / v 18:30."**
Vzhled 1:1 podle iOS verze (viz `design-podklady/DESIGN_SPEC.md` v iOS projektu), plus vlastní vylepšení navrch.

**Aktuální verze: 2.4** · Stack: Kotlin + Jetpack Compose, Google Maps, GeofencingClient, AlarmManager, Glance widget, FileProvider, CalendarContract · Minimum: Android 8 (API 26), cíl API 35

## Co appka umí

- Připomínky **na místo** (příjezd/odjezd, poloměr 50–1000 m, opakování) a **na čas** (jednorázově / denně / týdně ve vybrané dny)
- **Druhy upozornění**: Tiché / Výchozí / Naléhavé (budíkový zvuk do zavření) + **dožadování** (nepotvrzená připomínka se vrací à 5 min)
- Tlačítka na notifikaci: Hotovo · Odložit o hodinu · Zítra ráno
- **Živé našeptávání míst** (Photon – i názvy podniků, vzdálenosti, ikony, poslední a oblíbená místa)
- **Sdílení místa z Map Google** rovnou do připomínky, příjem geo: odkazů
- Oblíbená místa s čipy, mapový přehled připomínek
- **Widget** s tlačítkem +, rychlé akce ikony, přepínač vzhledu (systém/světlý/tmavý)
- Obnova hlídání po restartu, hlídač optimalizace baterie

### Novější funkce (v2.1–v2.4)

- **Navigovat** – u připomínky na místo otevřeš navigaci do cíle (Google Maps, jinak výběr mapové appky)
- **Hlasité čtení** připomínky nahlas – volitelné (Nastavení → Funkce)
- **Přílohy** k připomínce (foto, PDF, jiný soubor) – kopírují se do aplikace, přežijí přeinstalaci
- **Chytré seskupení** víc připomínek na jednom místě do jednoho souhrnného upozornění – volitelné
- **Import z Google Kalendáře** – z vybrané události uděláš připomínku (název, čas, případně místo)

## Jak sestavit APK

Nejjednodušší: **přilož tenhle projekt do session s Claudem** a napiš, co je potřeba. Claude projekt sestaví v cloudu a pošle hotové APK.

Ručně přes Android Studio:
1. Otevři tuhle složku v Android Studiu (Open → vybrat složku projektu).
2. Počkej, až se stáhnou závislosti (poprvé pár minut).
3. Build → Generate App Bundles or APKs → Generate APKs.
4. Hotové APK: `app/build/outputs/apk/release/app-release.apk`.

## Důležité soubory

- `mapskey.properties` – Google Maps klíč (`MAPS_API_KEY=AIza…`). Bez něj se appka sestaví, ale mapa bude prázdná.
- `app/georeminder.keystore` – podpisový klíč. Jen s ním jdou aktualizace instalovat přes starší verzi. Při nové verzi zvyš `versionCode` v `app/build.gradle.kts`.
- `PROJECT_STATUS.md` – aktuální stav projektu; přilož ho na začátku další session s Claudem.

> **Pozor:** `mapskey.properties` a `app/georeminder.keystore` jsou záměrně **mimo git** (`.gitignore`) – jsou to tajnosti. Kompletní kopie projektu **včetně nich** je v zipu ve složce `GeoReminder Android/Android/` na Jendově Macu. Po naklonování repozitáře je potřeba tyhle dva soubory doplnit odtamtud (bez keystore se APK podepíše jen vývojářským podpisem a nepůjde nainstalovat přes stávající verzi).

## Struktura kódu (`app/src/main/java/cz/jenda/georeminder/`)

- `model/` – datový model připomínky, přílohy a oblíbeného místa, české formátování (JSON kompatibilní s iOS verzí)
- `data/` – úložiště (JSON soubory), poloha, poslední místa, rozluštění sdílených odkazů, přílohy (`Attachments`), import z kalendáře (`CalendarImport`), přepínače funkcí (`FeatureSettings`)
- `notify/` – notifikace a kanály (vč. seskupení), plánovač (geofence + budíky + dožadování), přijímače událostí, obnova po restartu, navigace (`NavigationLauncher` / `NavigateActivity`), hlasité čtení (`TtsSpeaker`)
- `ui/` – obrazovky (seznam, formulář, výběr místa, oblíbená, mapa, onboarding, nastavení, výběr události z kalendáře) + iOS-look komponenty a téma
- `widget/` – Glance widget „Nejbližší připomínky"

## Dokumentace

- `PROJECT_STATUS.md` – aktuální stav projektu (přilož na začátku další session s Claudem)
- `IMPLEMENTACNI-PLAN.md` – plán oprav z auditu (hotovo)
- `IMPLEMENTACNI-PLAN-VYLEPSENI.md` – plán 5 schválených vylepšení (hotovo)
- `AUDIT1.md` – nezávislý audit aplikace
- `NAVOD-INSTALACE.md` – jak dostat APK do telefonu a co odklikat
- `GOOGLE-PLAY-CHECKLIST.md` – postup vydání na Google Play
- `GOOGLE-PLAY-TEXTY.md` – hotové texty pro Play (listing, Data safety, zdůvodnění oprávnění)
- `PRIVACY.md` / `privacy.html` – zásady ochrany soukromí (k vyvěšení na veřejnou URL)
