# GeoReminder (Android)

Nativní Android verze GeoReminderu – **„Připomeň mi X, až budu u Y / v 18:30."**
Vzhled 1:1 podle iOS verze (viz `design-podklady/DESIGN_SPEC.md` v iOS projektu), plus vlastní vylepšení navrch.

**Aktuální verze: 2.8** (versionCode 20) · Stack: Kotlin + Jetpack Compose, Google Maps, GeofencingClient, AlarmManager, Glance widget · Minimum: Android 8 (API 26), target 35

> **Aktuální stav:** viz `PROJECT_STATUS.md` a `AUDIT3.md` (finální audit a kompletní realizace oprav pro logiku, stabilitu, přístupnost i WCAG AAA/AA kontrast). Appka je stabilní, bezpečně ukládá data, neblokuje UI a je připravena k terénnímu testování před vydáním.

## Co appka umí

- Připomínky **na místo** (příjezd/odjezd, poloměr 50–1000 m, opakování) a **na čas** (jednorázově / denně / týdně ve vybrané dny)
- **Druhy upozornění**: Tiché / Výchozí / Naléhavé (budíkový zvuk do zavření) + **dožadování** (nepotvrzená připomínka se vrací à 5 min)
- Tlačítka na notifikaci: Hotovo · Odložit o hodinu · Zítra ráno · Navigovat (u míst)
- **Živé našeptávání míst** (Photon – i názvy podniků, vzdálenosti, ikony, poslední a oblíbená místa)
- **Sdílení místa z Map Google** rovnou do připomínky, příjem geo: odkazů
- **Přílohy** k připomínce (foto/PDF/soubor, kopie do appky), **hlasité čtení (TTS)**, **jednorázový import z Google Kalendáře** (volitelné v Nastavení)
- Oblíbená místa s čipy, mapový přehled připomínek
- **Widget** s tlačítkem +, rychlé akce ikony, přepínač vzhledu (systém/světlý/tmavý)
- Obnova hlídání po restartu telefonu
- Odolné asynchronní ukládání dat mimo hlavního UI vlákno, sanitace záloh a bezpečné mazání rozpracovaných příloh

## Jak sestavit APK

Nejjednodušší: **přilož tenhle projekt do session s AI asistentem** a napiš, co je potřeba. Asistent projekt sestaví v cloudu a pošle hotové APK.

Ručně přes Android Studio:
1. Otevři tuhle složku v Android Studiu (Open → vybrat složku projektu).
2. Počkej, až se stáhnou závislosti (poprvé pár minut).
3. Build → Generate App Bundles or APKs → Generate APKs.
4. Hotové APK: `app/build/outputs/apk/release/app-release.apk`.

## Důležité soubory

- `mapskey.properties` – Google Maps klíč (`MAPS_API_KEY=AIza…`). Bez něj se appka sestaví, ale mapa bude prázdná.
- `app/georeminder.keystore` – podpisový klíč. Jen s ním jdou aktualizace instalovat přes starší verzi. Při nové verzi zvyš `versionCode` v `app/build.gradle.kts`.
- `PROJECT_STATUS.md` – aktuální stav projektu.
- `AUDIT3.md` – finální kompletní audit a přehled nálezů (logika, provázanost, stabilita, čistota, UX, UI).

> **Pozor:** `mapskey.properties` a `app/georeminder.keystore` jsou záměrně **mimo git** (`.gitignore`) – jsou to tajnosti. Kompletní kopie projektu **včetně nich** je na Jendově Macu. Po naklonování repozitáře je potřeba tyhle dva soubory doplnit odtamtud (bez keystore se APK podepíše jen vývojářským podpisem a nepůjde nainstalovat přes stávající verzi).

## Struktura kódu (`app/src/main/java/cz/jenda/georeminder/`)

- `model/` – datový model připomínky a oblíbeného místa, české formátování (JSON kompatibilní s iOS verzí)
- `data/` – úložiště (JSON soubory), poloha, poslední místa, rozluštění sdílených odkazů, přílohy, import kalendáře, změřené systémové lišty, nastavení funkcí
- `notify/` – notifikace a kanály, plánovač (geofence + budíky + dožadování), přijímače událostí, obnova po restartu, TTS speaker
- `ui/` – obrazovky (seznam, formulář, výběr místa, oblíbená, mapa, onboarding, nastavení, import kalendáře) + iOS-look komponenty a témata
- `widget/` – Glance widget „Nejbližší připomínky"
