# GeoReminder – texty pro Google Play

**Aktualizováno:** 8. 9. 2026 pro build 2.7 / versionCode 19 / targetSdk 36.

> Store listing níže odpovídá současnému buildu. Texty pro Data safety a citlivá oprávnění jsou pracovní podklady, ne náhrada aktuálního formuláře Play Console. Před odesláním je vždy porovnej s otázkami, které Google zobrazí pro konkrétní `.aab`.

---

# 1. Store listing

## Název aplikace

```text
GeoReminder
```

## Krátký popis

```text
Připomínky na místo i na čas. Ozvou se tam a tehdy, kdy je potřebuješ.
```

## Dlouhý popis

```text
GeoReminder ti připomene věci podle místa nebo času.

„Připomeň mi koupit mléko, až budu u obchodu.“
„Vyzvedni balík, až budu odjíždět z práce.“
„Zavolej dnes v 18:30.“

CO GEOREMINDER UMÍ

• Připomínky na místo – upozornění při příchodu nebo odchodu z uživatelem zvoleného místa.
• Nastavitelný poloměr geofence a volitelné opakování.
• Připomínky na čas – jednorázově, každý den nebo týdně ve vybraných dnech.
• Odložení připomínky bez změny jejího původního pravidla.
• Akce přímo v notifikaci – Hotovo, Odložit, Zítra ráno a u míst také Navigovat.
• Tiché, výchozí nebo naléhavé upozornění a volitelné opakované připomenutí.
• Hledání míst přes Photon/OpenStreetMap s fallbackem systémového geokódování.
• Oblíbená a nedávno použitá místa.
• Sdílení podporovaných míst a geo: odkazů do nové připomínky.
• JPEG, PNG a PDF přílohy uložené v soukromém úložišti aplikace.
• Volitelné hlasité přečtení připomínky.
• Jednorázový import události z kalendáře.
• Mapový přehled připomínek.
• Widget s nejbližšími připomínkami.
• Čeština, angličtina nebo systémový jazyk aplikace.
• Světlý, tmavý, neutrální a glass vzhled.
• Obnova alarmů a geofence po restartu telefonu.
• ZIP záloha připomínek, oblíbených míst a podporovaných příloh.
• Diagnostika oprávnění, geofence, alarmů, snooze a datového stavu.

SOUKROMÍ

GeoReminder nemá vlastní uživatelské účty, reklamy ani vlastní analytický backend. Připomínky a spravované přílohy jsou uložené v soukromém úložišti aplikace. Poloha se používá pro geofencing; aplikace nevytváří vlastní historii pohybu ani ji neodesílá na server provozovatele.

Některé funkce využívají služby třetích stran, například Google Maps/Google Play Services a Photon/OpenStreetMap. Podrobnosti jsou v zásadách ochrany soukromí.

POLOHA NA POZADÍ

Aby mohla připomínka na místo fungovat i bez otevřené aplikace, GeoReminder používá Android geofencing a může potřebovat přístup k poloze na pozadí. Oprávnění slouží k detekci příchodu nebo odchodu z míst, která si uživatel sám uložil.
```

## Co je nového – build 2.7 / stabilizace API 36

```text
• Připraveno pro Android 16 / API 36
• Spolehlivější obnova alarmů a připomínek po restartu
• Opravené Snooze a opakované časové připomínky
• Bezpečnější obnova poškozených dat a ZIP zálohy s přílohami
• CZ/EN/SYSTEM jazyk aplikace
• Diagnostika oprávnění a doručování
• Řada oprav notifikací, geofence, importu kalendáře a widgetu
```

---

# 2. Podklad pro background location declaration

## Zdůvodnění

```text
GeoReminder je aplikace pro připomínky vázané na místo. Uživatel si může vytvořit připomínku, která se má zobrazit při příchodu na konkrétní místo nebo při odchodu z něj. Aby tato základní funkce fungovala i tehdy, když není aplikace právě otevřená, používá GeoReminder geofencing systému Android a potřebuje přístup k poloze na pozadí.

Poloha na pozadí se používá pouze pro detekci příchodu/odchodu z míst, která si uživatel sám nastavil. GeoReminder nevytváří vlastní historii pohybu a neodesílá polohu na server provozovatele aplikace.
```

## Prominent disclosure – pracovní text

```text
GeoReminder používá polohu i na pozadí, aby vás mohl upozornit při příchodu na místo připomínky nebo při odchodu z něj, i když aplikace není otevřená. Aplikace nevytváří vlastní historii vašeho pohybu ani ji neposílá na náš server.
```

Před Play review ověřit, že disclosure je skutečně zobrazený ve správném místě před žádostí o background permission a že případné demonstrační video odpovídá reálnému toku aplikace.

---

# 3. Přesné alarmy

GeoReminder deklaruje:

```text
android.permission.SCHEDULE_EXACT_ALARM
```

Nepoužívá `USE_EXACT_ALARM`.

Pracovní vysvětlení, pokud se Play Console na použití zeptá:

```text
GeoReminder umožňuje uživateli vytvořit časovou připomínku na konkrétní čas. Pro co nejpřesnější doručení používá special access SCHEDULE_EXACT_ALARM, který uživatel uděluje v systému Android. Pokud přístup není povolený, aplikace používá méně přesný AlarmManager fallback a stav je viditelný v diagnostice.
```

---

# 4. Čtení kalendáře

```text
GeoReminder umožňuje uživateli jednorázově importovat vybranou nadcházející událost z kalendáře do nové připomínky. Oprávnění READ_CALENDAR se používá pouze po uživatelské akci k zobrazení dostupných událostí a převzetí vybraných údajů do reminderu. Aplikace kalendář needituje a nejde o průběžnou synchronizaci.
```

---

# 5. Data safety – pracovní podklad

Finální odpovědi musí být vyplněny podle aktuálních definic Play Console pro `collected`, `shared`, účely zpracování a případné výjimky poskytovatelů služeb.

Fakta o aplikaci:

- nemá vlastní účet/login,
- nemá reklamy,
- nemá vlastní analytics/tracking SDK,
- ukládá reminder texty, místa a přílohy lokálně,
- používá přesnou/přibližnou/background location pro geofencing,
- používá Google Maps/Google Play Services,
- posílá text hledání službě Photon,
- může použít systémový Geocoder,
- může načíst uživatelem sdílený mapový odkaz,
- může číst kalendář při uživatelském importu,
- Android cloud backup/device transfer může přenést `reminders.json`, `favorites.json` a spravované přílohy podle systémového nastavení,
- ruční ZIP backup může uživatel uložit do jím zvoleného úložiště.

Před vyplněním Data safety porovnat formulář s `PRIVACY.md` a `GOOGLE-PLAY-CHECKLIST.md`.

---

# 6. Store grafika a screenshoty

Před nahráním:

- ikona 512×512,
- feature graphic 1024×500,
- aktuální screenshoty z build 2.7 nebo novějšího release kandidáta,
- žádné screenshoty starých/odstraněných přepínačů,
- nepoužívat text „libovolný soubor“ u příloh – podporované jsou JPEG, PNG a PDF,
- nepoužívat staré tvrzení `target API 35`,
- případná diagnostická obrazovka smí ukazovat pouze anonymizované technické údaje.

Vhodné screenshoty:

1. hlavní seznam připomínek,
2. vytvoření location reminderu / výběr místa,
3. time reminder,
4. notifikace s akcemi,
5. mapa nebo widget,
6. diagnostika spolehlivosti.
