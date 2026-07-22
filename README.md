# GeoReminder (Android)

Nativní Android verze GeoReminderu – **„Připomeň mi X, až budu u Y / v 18:30."**
Vzhled 1:1 podle iOS verze (viz `design-podklady/DESIGN_SPEC.md` v iOS projektu), plus vlastní vylepšení navrch.

**Aktuální verze: 2.5** (versionCode 17) · Stack: Kotlin + Jetpack Compose, Google Maps, GeofencingClient, AlarmManager, Glance widget · Minimum: Android 8 (API 26), target 35

> **Aktuální stav a známé problémy:** viz `PROJECT_STATUS.md` a hlavně `AUDIT2.md` (komplexní audit přes 6 dimenzí). Appka má solidní jádro, ale pár funkcí (seskupení notifikací, plná anglická lokalizace, kontrast dle WCAG) je ještě rozdělaných – detaily v auditu.

## Co appka umí

- Připomínky **na místo** (příjezd/odjezd, poloměr 50–1000 m, opakování) a **na čas** (jednorázově / denně / týdně ve vybrané dny)
- **Druhy upozornění**: Tiché / Výchozí / Naléhavé (budíkový zvuk do zavření) + **dožadování** (nepotvrzená připomínka se vrací à 5 min)
- Tlačítka na notifikaci: Hotovo · Odložit o hodinu · Zítra ráno · Navigovat (u míst)
- **Živé našeptávání míst** (Photon – i názvy podniků, vzdálenosti, ikony, poslední a oblíbená místa)
- **Sdílení místa z Map Google** rovnou do připomínky, příjem geo: odkazů
- **Přílohy** k připomínce (foto/PDF/soubor, kopie do appky), **hlasité čtení (TTS)**, **jednorázový import z Google Kalendáře** (volitelné, v Nastavení → Funkce)
- Oblíbená místa s čipy, mapový přehled připomínek
- **Widget** s tlačítkem +, rychlé akce ikony, přepínač vzhledu (systém/světlý/tmavý)
- Obnova hlídání po restartu, hlídač optimalizace baterie

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
- `AUDIT2.md` – komplexní audit (logika, provázanost, stabilita, čistota, UX, UI) s konkrétními nálezy.

> **Pozor:** `mapskey.properties` a `app/georeminder.keystore` jsou záměrně **mimo git** (`.gitignore`) – jsou to tajnosti. Kompletní kopie projektu **včetně nich** je na Jendově Macu. Po naklonování repozitáře je potřeba tyhle dva soubory doplnit odtamtud (bez keystore se APK podepíše jen vývojářským podpisem a nepůjde nainstalovat přes stávající verzi).

## Struktura kódu (`app/src/main/java/cz/jenda/georeminder/`)

- `model/` – datový model připomínky a oblíbeného místa, české formátování (JSON kompatibilní s iOS verzí)
- `data/` – úložiště (JSON soubory), poloha, poslední místa, rozluštění sdílených odkazů, přílohy, import kalendáře, změřené systémové lišty
- `notify/` – notifikace a kanály, plánovač (geofence + budíky + dožadování), přijímače událostí, obnova po restartu, TTS
- `ui/` – obrazovky (seznam, formulář, výběr místa, oblíbená, mapa, onboarding, nastavení, import kalendáře) + iOS-look komponenty a téma
- `widget/` – Glance widget „Nejbližší připomínky"
