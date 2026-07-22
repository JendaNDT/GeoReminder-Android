# GeoReminder – texty pro Google Play

*Připraveno 22. 07. 2026 pro verzi v2.4. Vše česky (výchozí jazyk pro Play = čeština). Kde je limit znaků, je uvedený. Bloky jsou psané tak, abys je mohl rovnou zkopírovat do Play konzole. Anglickou verzi můžu doplnit, kdybys chtěl cílit i mimo ČR – jen řekni.*

> **Poznámka:** Nejsem právník ani zaměstnanec Googlu. Texty k oprávněním a Data safety jsou napsané pravdivě podle toho, jak appka reálně funguje, ale přesné znění formulářů v konzoli se čas od času mění – před odesláním si je s formulářem porovnej a řiď se smyslem.

---

## 1. Store listing (jak appka vypadá v obchodě)

### Název aplikace (max 30 znaků)
```
GeoReminder
```

### Krátký popis (max 80 znaků)
```
Připomeň mi to, až budu u obchodu – připomínky na místo i na čas.
```
*(65 znaků)*

### Dlouhý popis (max 4000 znaků)
```
GeoReminder ti připomene věci ve správný okamžik – buď podle místa, nebo podle času.

„Připomeň mi koupit mléko, až budu u obchodu." „Vyzvedni balík, až budeš odjíždět z práce." „Zavolej mámě dnes v 18:30." GeoReminder hlídá polohu i čas za tebe a ozve se přesně tehdy, kdy to potřebuješ – i když máš appku zavřenou.

CO APPKA UMÍ

• Připomínky na místo – ozve se při příjezdu nebo odjezdu z místa, které vybereš na mapě. Nastavíš si poloměr (50–1000 m) a případně opakování při každém příjezdu.
• Připomínky na čas – jednorázově, každý den, nebo každý týden ve vybrané dny.
• Hledání míst našeptáváním – píšeš a rovnou vidíš výsledky včetně názvů podniků, vzdálenosti a typu místa. Oblíbená a naposledy použitá místa máš po ruce.
• Druhy upozornění – Tiché, Výchozí, nebo Naléhavé (budíkový zvuk, dokud notifikaci nezavřeš). Volitelně „dožadování": nepotvrzená připomínka se vrací každých 5 minut.
• Tlačítka přímo na notifikaci – Hotovo, Odložit o hodinu, Zítra ráno.
• Navigovat – u připomínky na místo otevřeš jedním ťuknutím navigaci do cíle.
• Hlasité čtení – po spuštění ti připomínku přečte telefon nahlas (volitelné).
• Přílohy – k připomínce připojíš fotku, PDF nebo jiný soubor (třeba účtenku nebo lístek). Zůstávají uložené v aplikaci.
• Chytré seskupení – víc připomínek na jednom místě se zobrazí jako jedno přehledné upozornění (volitelné).
• Import z kalendáře – z události v kalendáři uděláš jedním ťuknutím připomínku (převezme název, čas a případně místo).
• Sdílení místa z Map Google rovnou do připomínky.
• Widget na plochu s nejbližšími připomínkami a tlačítkem pro rychlé přidání.
• Světlý i tmavý vzhled.
• Po restartu telefonu se hlídání samo obnoví.

SOUKROMÍ

GeoReminder je jednoduchá appka bez účtů, bez reklam a bez sledování. Připomínky, oblíbená místa i přílohy zůstávají ve tvém telefonu. Poloha se používá jen k hlídání míst přímo v zařízení – neposíláme ji na žádný náš server. Mapu vykresluje Google Maps a hledání míst zajišťuje služba Photon (OpenStreetMap); těm se předává jen to, co je nutné pro zobrazení mapy a vyhledávání. Podrobnosti jsou v zásadách ochrany soukromí.

PROČ POLOHA NA POZADÍ

Aby ti připomínka na místo přišla i se zavřenou appkou, potřebuje GeoReminder přístup k poloze „Povolit vždy". Polohu používá výhradně k tomu, aby poznal, že jsi dorazil na místo připomínky. Nikam ji neodesíláme.
```

### Poznámky k verzi / Co je nového (max 500 znaků) – v2.4
```
Novinky:
• Navigovat – u připomínky na místo otevřeš navigaci jedním ťuknutím
• Hlasité čtení připomínky (volitelné, v Nastavení → Funkce)
• Přílohy k připomínce (foto, PDF…) uložené v aplikaci
• Chytré seskupení víc připomínek na jednom místě (volitelné)
• Import události z Google Kalendáře do připomínky
Plus drobná vylepšení a opravy.
```
*(Cca 300 znaků. Můžu zkrátit/upravit.)*

### Kategorie a další pole
- **Kategorie aplikace:** Produktivita (Productivity).
- **Tagy / štítky:** připomínky, úkoly, poloha, geofencing, kalendář (vyber z nabídky ty, co konzole dovolí).
- **Kontaktní e-mail:** tvůj (mcnegr@gmail.com), případně web/telefon nepovinně.
- **Grafika (nutná, dodám samostatně, až řekneš):** ikona 512×512 PNG, feature graphic 1024×500, min. 2–3 screenshoty z telefonu (na screenshoty se hodí: seznam připomínek, výběr místa na mapě, detail připomínky s přílohou, notifikace s tlačítkem Navigovat).

---

## 2. Data safety (bezpečnost dat) – odpovědi do formuláře

Play se ptá, jaká data appka **shromažďuje** (= odesílá ze zařízení) a **sdílí**. GeoReminder nemá vlastní server, účty ani reklamy. Data o připomínkách, oblíbených místech a přílohách zůstávají v telefonu. Pravdivé odpovědi:

**Shromažďuje nebo sdílí appka nějaká uživatelská data?**
- Odpověz **Ano** (kvůli poloze – viz níže; appka sice nic neukládá na tvůj server, ale poloha se předává mapové a vyhledávací službě, takže „opouští zařízení").

**Typy dat:**

| Typ dat | Shromažďuje se? | Sdílí se? | Účel | Poznámka |
|---|---|---|---|---|
| **Poloha (přesná i přibližná)** | Ano | Ne* | Funkčnost aplikace | Používá se k hlídání míst v zařízení; k vykreslení mapy se předává Google Maps SDK. Neukládáme na žádný náš server. |
| Osobní údaje (jméno, e-mail, tel.) | Ne | Ne | – | Appka nemá účty ani přihlašování. |
| Kontakty | Ne | Ne | – | – |
| Kalendář | Ne | Ne | – | Kalendář se **jen čte v zařízení** při importu události; nikam se neodesílá. |
| Fotky / soubory (přílohy) | Ne | Ne | – | Přílohy se kopírují do soukromého úložiště appky, zůstávají v telefonu. |
| Zprávy, kontakty, platby, zdraví | Ne | Ne | – | – |

*„Sdílení" ve smyslu Play = předání třetí straně pro její vlastní účely. Google Maps a Photon vystupují jako poskytovatelé služby (mapa, hledání), ne jako příjemci dat pro reklamu.

**Další otázky ve formuláři (doporučené odpovědi):**
- **Jsou data šifrovaná při přenosu?** Ano (veškerá síťová komunikace jde přes HTTPS).
- **Můžou uživatelé požádat o smazání dat?** Ano – smazáním připomínek v appce nebo odinstalací appky se data odstraní. (Appka žádná data neshromažďuje na server, takže není co dál mazat.)
- **Shromažďují se data od dětí?** Ne / appka není cílená na děti.

> Pozor: kvůli poloze a použití Google Maps SDK Play obvykle očekává, že polohu deklaruješ jako „shromažďovanou" (protože technicky opouští zařízení směrem ke Google). Proto výše uvádím Poloha = Ano. Zbytek (kalendář, přílohy, kontakty) zůstává v zařízení = Ne.

---

## 3. Zdůvodnění oprávnění (pro formuláře „App access" / deklarace)

Pokud Play při odeslání chce vysvětlení citlivých oprávnění, použij tyto texty.

### Poloha na pozadí (ACCESS_BACKGROUND_LOCATION) – POVINNÁ zvláštní deklarace
**Proč to appka potřebuje (do zdůvodňovacího formuláře):**
```
GeoReminder je připomínkovač vázaný na místo. Hlavní funkcí aplikace je upozornit uživatele, když dorazí na místo, které si sám zvolil (např. „připomeň mi koupit mléko, až budu u obchodu"). Aby toto fungovalo i se zavřenou aplikací a zhasnutou obrazovkou, používá aplikace geofencing systému Android, který vyžaduje přístup k poloze na pozadí („Povolit vždy"). Poloha se používá výhradně k detekci příchodu/odchodu z uživatelem zvoleného místa a k zobrazení místní připomínky. Poloha se neodesílá na žádný server provozovatele, nesdílí se s třetími stranami pro reklamu a neukládá se mimo zařízení.
```
**Prominentní sdělení (prominent disclosure) – text, který uvidí uživatel v aplikaci před udělením oprávnění.** Aplikace ho ukazuje v onboardingu; pro jistotu ho můžeš uvést i do deklarace:
```
GeoReminder používá vaši polohu (i na pozadí, „Povolit vždy") k tomu, aby vás upozornil, když dorazíte na místo připomínky – i když je aplikace zavřená. Polohu nikam neodesíláme.
```
**Pozn.:** Play u polohy na pozadí často žádá **krátké video** (30–120 s), které scénář ukazuje: vytvoření připomínky na místo → příchod na místo → notifikace. Nahraješ ho k deklaraci (stačí záznam obrazovky telefonu). Play v roce 2026 pravidla polohy zpřísnil – u deklarace uvidíš aktuální požadavky, drž se jich.

### Přesné budíky (USE_EXACT_ALARM)
```
GeoReminder je připomínkovací aplikace, která doručuje časové připomínky v přesný čas zvolený uživatelem (např. „připomeň mi v 18:30"). K tomu používá přesné budíky (exact alarms). Aplikace spadá do kategorie budík/kalendář/připomínky, pro kterou je toto oprávnění určené.
```
*(Kdyby to dělalo problém, mám v záloze variantu bez `USE_EXACT_ALARM` – uživatel by si přesné budíky povoloval sám v nastavení. Řekni a upravím.)*

### Čtení kalendáře (READ_CALENDAR) – NOVÉ ve v2.4
```
GeoReminder umožňuje uživateli jednorázově importovat událost z kalendáře a udělat z ní připomínku (převezme název, čas začátku a případně místo). Oprávnění ke čtení kalendáře se používá výhradně k zobrazení seznamu nadcházejících událostí, ze kterých si uživatel vybere. Aplikace kalendář jen čte v zařízení, nic z něj neodesílá ani neupravuje. Nejde o průběžnou synchronizaci.
```

---

## 4. Content rating (věkové hodnocení)
Dotazník vyplň pravdivě – appka **neobsahuje** násilí, sex, vulgaritu, hazard, drogy ani uživatelskou komunikaci. Výsledek bude nejnižší hodnocení (vhodné pro všechny / PEGI 3). Kategorie aplikace: nástroj/produktivita (ne hra).

---

## 5. Cílová skupina a obsah
- **Cílová věková skupina:** dospělí / 13+ (ne appka pro děti). V dotazníku „Target audience" nevybírej dětské kategorie.
- **Reklamy:** appka neobsahuje reklamy → v listingu označ „Obsahuje reklamy: Ne".
- **Nákupy v aplikaci:** žádné.

---

## 6. Co je potřeba dodat mimo texty (připomínka)
- **Zásady ochrany soukromí** – hotový text je v `PRIVACY.md` / `privacy.html`. Vyvěsíš na veřejnou URL (GitHub Pages / Google Sites) a vložíš do App content → Privacy policy. Návod na vyvěšení je v checklistu.
- **Grafika** (ikona 512, feature graphic, screenshoty) – vyrobím, až řekneš.
- **`.aab`** místo APK – sestavím podepsaný tvým klíčem, až budeš mít účet a appku založenou (a až zapneme minifikaci a otestuješ ji).
