# Zásady ochrany soukromí – GeoReminder

**Poslední aktualizace: 8. 9. 2026**

Tyto zásady popisují, jak aplikace **GeoReminder** pro Android nakládá s daty. Aplikace nemá vlastní uživatelské účty, reklamy ani vlastní analytický backend.

Provozovatel aplikace: **Jenda** (kontakt: mcnegr@gmail.com).

## Data uložená v zařízení

GeoReminder ukládá do soukromého úložiště aplikace zejména:

- připomínky a jejich text,
- uložená/oblíbená místa, souřadnice a poloměr,
- čas a pravidlo opakování,
- nastavení upozornění,
- uživatelem přidané přílohy typu JPEG, PNG nebo PDF,
- technický stav nutný pro spolehlivé doručení (například snooze, fired/registration state),
- omezenou technickou diagnostickou historii.

Diagnostická historie je omezena na posledních 50 technických událostí a záměrně neukládá názvy reminderů, jejich interní ID ani přesné GPS souřadnice uživatele.

## Poloha

Aplikace používá přesnou/přibližnou polohu a podle nastavení Androidu také polohu na pozadí k funkci geofencingu: připomínka se může spustit při příchodu na zadané místo nebo při odchodu z něj i tehdy, když aplikace není právě otevřená.

GeoReminder nevytváří vlastní historii pohybu uživatele a neposílá polohu na server provozovatele aplikace. Samotné vyhodnocování geofence zajišťuje Android/Google Play Services v zařízení.

Uložená místa reminderů samozřejmě obsahují souřadnice, protože bez nich by geografická připomínka měla poněkud těžký pracovní den.

## Kalendář

Aplikace může po výslovné akci uživatele číst nadcházející události z kalendáře a nabídnout jejich jednorázový import do reminderu. Kalendář needituje a jeho obsah neodesílá na server provozovatele aplikace.

## Kdy data opouštějí zařízení

GeoReminder nemá vlastní server, ale některé funkce používají služby třetích stran:

- **Google Maps SDK / Google Play Services** – vykreslení mapy, geofencing a související systémové funkce,
- **Photon / OpenStreetMap** – při hledání místa se odešle text hledaného dotazu,
- **systémový Geocoder** – může zpracovat text adresy jako fallback při hledání/importu,
- **rozbalení sdíleného odkazu** – pokud uživatel nasdílí podporovaný mapový odkaz, aplikace může daný odkaz načíst, aby zjistila cílové místo.

Komunikace aplikace s internetovými službami probíhá přes HTTPS tam, kde ji GeoReminder přímo vytváří.

Třetí strany mohou zpracovávat technické údaje podle svých vlastních zásad ochrany soukromí. Pro Data safety formulář v Google Play je potřeba vycházet z aktuálních definic Googlu pro „collected“ a „shared“, ne pouze z toho, zda data vidí provozovatel GeoReminderu.

## Zálohování a přenos zařízení

Android může podle nastavení uživatele zálohovat nebo přenést:

- `reminders.json`,
- `favorites.json`,
- spravované soubory v adresáři `attachments/`.

Cloud backup je spravovaný operačním systémem/účtem Google. Provozovatel GeoReminderu k této záloze nemá vlastní přístup.

Aplikace navíc umožňuje ruční export/import vlastního ZIP backupu. Backup může obsahovat remindery, oblíbená místa a podporované spravované přílohy. Starší JSON backupy jsou podporované pro zpětnou kompatibilitu.

## Oprávnění

Aplikace může používat:

- **přibližnou a přesnou polohu** – výběr a hlídání míst,
- **polohu na pozadí** – geofence připomínky bez otevřené Activity,
- **notifikace** – doručení připomínek,
- **SCHEDULE_EXACT_ALARM** – uživatelsky udělovaný special access pro přesnější časové remindery,
- **spuštění po restartu** – obnovení alarmů/geofence,
- **vyjmutí z optimalizace baterie** – volitelná podpora spolehlivosti na pozadí,
- **internet** – mapy, hledání míst a zpracování podporovaných sdílených odkazů,
- **čtení kalendáře** – pouze při použití importu z kalendáře.

Pokud přesné alarmy nejsou povolené, aplikace používá méně přesný AlarmManager fallback a stav zpřístupňuje v diagnostice.

## Co aplikace nedělá

- Nemá vlastní uživatelské účty ani přihlašování.
- Neobsahuje reklamy.
- Neobsahuje vlastní analytiku ani reklamní tracking SDK.
- Neprodává data uživatelů.
- Nevytváří vlastní serverovou databázi reminderů nebo historie polohy.

## Kontrola a mazání dat

Uživatel může data odstranit:

- smazáním reminderů/oblíbených míst/příloh v aplikaci,
- vymazáním dat aplikace v systému Android,
- odinstalací aplikace.

Ruční backup vytvořený uživatelem je samostatný soubor a musí být odstraněn tam, kam jej uživatel uložil.

## Děti

Aplikace není navržena jako služba určená dětem a záměrně od dětí neshromažďuje osobní údaje.

## Změny zásad

Tyto zásady mohou být při změně funkcí nebo požadavků Google Play aktualizovány. Datum poslední aktualizace je uvedeno nahoře.

## Kontakt

Dotazy k ochraně soukromí: **mcnegr@gmail.com**
