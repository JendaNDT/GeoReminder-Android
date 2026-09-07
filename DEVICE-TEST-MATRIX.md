# GeoReminder – device test matrix

Aktualizováno: 7. 9. 2026

Tento dokument je povinný release checklist pro scénáře, které nelze spolehlivě dokázat obyčejným JVM unit testem.

## Cílová zařízení

| Platforma | Minimum | Účel |
|---|---:|---|
| Android 8 / API 26 emulator | povinné | minSdk, staré permission a background chování |
| Android 13 / API 33 emulator | povinné | POST_NOTIFICATIONS a moderní runtime permissions |
| Android 14 / API 34 emulator | povinné | exact alarm fresh-install chování |
| Android 15 / API 35 emulator | povinné | předchozí stabilní Android |
| Android 16 / API 36 emulator | povinné | aktuální targetSdk a behavior changes |
| Pixel / čistý Android | povinné před closed testem | referenční zařízení bez OEM omezení |
| Samsung / One UI | povinné před closed testem | hlavní reálný OEM scénář, background/battery omezení |

## Kritické scénáře

Každý řádek musí před veřejným releasem skončit PASS nebo mít explicitně zdokumentované platformní omezení.

| # | Scénář | Automatizace | Samsung | Pixel / emulator | Stav |
|---:|---|---|---|---|---|
| 1 | vytvořit one-time time reminder → systémový alarm existuje | instrumentation | ☐ | ☐ | ☐ |
| 2 | editovat time reminder → starý alarm zrušen, nový aktivní | instrumentation + manual | ☐ | ☐ | ☐ |
| 3 | označit Hotovo → alarm/snooze/nag zrušen | instrumentation | ☐ | ☐ | ☐ |
| 4 | snooze time reminderu → původní trigger pozastaven | instrumentation | ☐ | ☐ | ☐ |
| 5 | snooze location reminderu → původní geofence pozastavena | manual/device | ☐ | ☐ | ☐ |
| 6 | restart telefonu během snooze → snooze obnoven | manual/device | ☐ | ☐ | ☐ |
| 7 | restart telefonu s aktivním time reminderem | manual/device | ☐ | ☐ | ☐ |
| 8 | restart telefonu s aktivní geofence | manual/device | ☐ | ☐ | ☐ |
| 9 | telefon vypnutý přes termín → po bootu catch-up bez duplicity | manual/device | ☐ | ☐ | ☐ |
| 10 | kill procesu → receiver načte data z disku | instrumentation | ☐ | ☐ | ☐ |
| 11 | force-stop → zdokumentovat Android omezení do dalšího ručního spuštění | manual | ☐ | ☐ | ☐ |
| 12 | revoke fine location | instrumentation + manual | ☐ | ☐ | ☐ |
| 13 | revoke background location | instrumentation + manual | ☐ | ☐ | ☐ |
| 14 | exact alarm denied / granted | manual/device | ☐ | ☐ | ☐ |
| 15 | notifications denied / granted | manual/device | ☐ | ☐ | ☐ |
| 16 | vypnout systémovou polohu | manual/device | ☐ | ☐ | ☐ |
| 17 | battery saver / OEM optimalizace | manual/device | ☐ | ☐ | ☐ |
| 18 | 100 aktivních geofence + 101. čekající | unit + manual | ☐ | ☐ | ☐ |
| 19 | změna timezone s DAILY/WEEKLY reminderem | unit + emulator | ☐ | ☐ | ☐ |
| 20 | jarní DST gap | unit + emulator | n/a | ☐ | ☐ |
| 21 | podzimní DST overlap | unit + emulator | n/a | ☐ | ☐ |
| 22 | dvojité Hotovo/Snooze z jedné notifikace | instrumentation + manual | ☐ | ☐ | ☐ |
| 23 | click notifikace při cold-startu otevře správný reminder | manual/device | ☐ | ☐ | ☐ |
| 24 | CZ/EN/SYSTEM po cold-start notifikaci/widgetu/TTS | manual/device | ☐ | ☐ | ☐ |
| 25 | ZIP backup → import s JPEG/PNG/PDF | manual/device | ☐ | ☐ | ☐ |

## Automatické testy v repozitáři

### JVM unit tests

- scheduler daily/weekly matematika
- ISO weekday 1–7
- DST a přechod roku
- corrupted/partial/valid JSON
- Apple date / iOS kompatibilita
- backup sanitace, deduplikace a path traversal
- geofence validační politika a limit 100
- calendar instance deduplikace
- map link validace
- widget ordering
- CZ/EN resource parity

### Android instrumentation

- perzistence snooze a stabilních requestCode
- jednorázové notification action tokeny
- AlarmReceiver cold-start načtení z disku
- NotificationActionReceiver cold-start + idempotentní Hotovo
- fine/background location grant/revoke
- exact alarm capability proti skutečnému AlarmManageru
- snooze PendingIntent a perzistentní snooze stav

## Pravidlo pro release

Zelený CI build neznamená automaticky připravenost k veřejnému vydání. Před closed/public track musí být dokončen minimálně Samsung/One UI a čistý Android/Pixel sloupec u všech kritických scénářů, které jsou označené jako `manual/device`.
