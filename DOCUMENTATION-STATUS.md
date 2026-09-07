# GeoReminder – stav dokumentace

**Aktualizováno:** 8. 9. 2026

Tento index určuje, které dokumenty představují současný stav a které jsou historické. Pokud si dva dokumenty odporují, platí pořadí zdrojů pravdy níže.

## Aktuální zdroje pravdy

| Dokument | Stav | Účel |
|---|---|---|
| `PROJECT_STATUS.md` | **AKTUÁLNÍ** | skutečný build, SDK, hotové etapy a release blokery |
| `IMPLEMENTACNI-PLAN-OPRAV-2026-09.md` | **AKTUÁLNÍ / DOKONČOVANÝ** | stabilizační plán Etap 1–12 a Definition of Done |
| `DEVICE-TEST-MATRIX.md` | **AKTUÁLNÍ** | manuální/device release gate |
| `GOOGLE-PLAY-CHECKLIST.md` | **AKTUÁLNÍ** | Google Play policy a release checklist |
| `PRIVACY.md` | **AKTUÁLNÍ** | privacy policy odpovídající současnému chování |
| `README.md` | **AKTUÁLNÍ** | vstupní technický přehled projektu |

## Historické audity

| Dokument | Stav | Poznámka |
|---|---|---|
| `AUDIT1.md` | **HISTORICKÝ** | statický audit v1.6 / versionCode 8 / target 35 z 21. 7. 2026; většina kritických nálezů byla později řešena stabilizačním plánem |
| `AUDIT3.md` | **HISTORICKÝ** | snapshot z 23. 7. 2026 kolem v2.5; není současným zdrojem pravdy |

Historické audity se záměrně nemažou. Jsou auditní stopou toho, proč určité opravy vznikly. Čísla verzí, SDK a seznam „otevřených“ problémů v nich ale nepopisují dnešní kód.

## Starší implementační plány

| Dokument | Stav | Poznámka |
|---|---|---|
| `IMPLEMENTACNI-PLAN.md` | **NAHRAZENÝ** | starší implementační plán; pro stabilizaci a release byl nahrazen `IMPLEMENTACNI-PLAN-OPRAV-2026-09.md` |
| `IMPLEMENTACNI-PLAN-VYLEPSENI.md` | **ODLOŽENÝ / NAHRAZENÝ PRO RELEASE** | backlog vylepšení; nové funkce nejsou release prioritou, dokud není dokončen hardening a device matrix |
| `IMPLEMENTACNI-PLAN-OPRAV-2026-09.md` | **AKTUÁLNÍ** | autoritativní plán oprav a release hardeningu |

## Další dokumenty

| Dokument | Stav |
|---|---|
| `GOOGLE-PLAY-TEXTY.md` | **K REVIZI PŘED POUŽITÍM** – store texty musí být před uploadem porovnány s aktuální aplikací |
| `NAVOD-INSTALACE.md` | **POMOCNÝ** – postup lokální instalace, není zdrojem technického stavu |

## Pořadí při rozporu

1. skutečný kód a `app/build.gradle.kts`,
2. `PROJECT_STATUS.md`,
3. `IMPLEMENTACNI-PLAN-OPRAV-2026-09.md`,
4. aktuální release/device checklisty,
5. historické audity a staré plány.

Aktuální build při vytvoření tohoto indexu: **2.7 / versionCode 19 / minSdk 26 / targetSdk 36**.
