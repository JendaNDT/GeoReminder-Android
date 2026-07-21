# GeoReminder – checklist pro vydání na Google Play

*Sestaveno 21. 07. 2026 (Fáze 3). Cíl: dostat appku na Google Play. Rozděleno na to, co je **hotové v kódu (v2.0)**, co **udělám já v kódu**, až řekneš, a co **musíš odkliknout ty** v Play konzoli. Kroky v konzoli píšu pro necodera; přesné názvy tlačítek se můžou drobně měnit, řiď se smyslem.*

---

## ⚠️ Přečti si nejdřív tohle (zásadní pro plán)

**Nové osobní vývojářské účty musí před vydáním do produkce projít uzavřeným testem: minimálně 12 testerů, kteří appku mají nainstalovanou nepřetržitě aspoň 14 dní.** Teprve pak Google odemkne tlačítko „vydat do produkce". Není to volitelné. Takže reálná časová osa vydání není „za víkend", ale spíš **2–3 týdny** kvůli tomu 14dennímu testu. Počítej s tím dopředu a sežeň si ~12 lidí (rodina, kamarádi, kolegové), kteří ti pomůžou testovat.

*(Firemní/organizační účet tuhle podmínku nemá, ale zakládá se jinak a je dražší – pro osobní appku dává smysl osobní účet a projít testem.)*

---

## Část A — ✅ Hotové v kódu (v2.0)

Tohle jsem už udělal, nemusíš řešit:

- **Cílový API 35 (Android 15)** – splňuje požadavek Google Play pro rok 2026 (nové appky musí cílit na API 35). ✓
- **Řízené zálohování dat** – appka zálohuje do tvého Google účtu jen připomínky a oblíbená místa (`data_extraction_rules.xml` / `full_backup_content.xml`), takže přežijí výměnu telefonu. Přechodné plánovací značky se zálohují záměrně NE. ✓
- **Minifikace (R8/ProGuard) připravená** – pravidla jsou napsaná (`proguard-rules.pro`) a zadrátovaná, jen je zatím **vypnutá** (viz Část B, bod 1 – zapne se až po testu na zařízení). ✓
- **Podpisový klíč** – stále stejný keystore (CN=GeoReminder), pro Play se z něj stane „upload key" (viz Část C, bod 7). ✓
- **Balíček aplikace** – appka jede jako jeden modul, žádné blokující technické překážky pro Play.

## Část B — 🔧 Co udělám já v kódu, až řekneš

Tyhle věci jsou na mně, ale dělají se až těsně před uploadem (nebo podle tvého rozhodnutí):

1. **Zapnout minifikaci a otestovat.** Přepnu `isMinifyEnabled = true`, sestavím, a je potřeba ověřit na tvém telefonu, že se pořád dají ukládat/číst připomínky (minifikace umí rozbít JSON, když nejsou dobrá pravidla – proto to chci nejdřív otestovat, ne zapnout naslepo). Zmenší to appku a je to pro Play doporučené (ne povinné).
2. **Sestavit App Bundle (`.aab`), ne APK.** Google Play přijímá **Android App Bundle** (`.aab`), ne holé APK. Až budeš mít účet a appku založenou, sestavím `.aab` podepsaný tvým klíčem.
3. **Drobné úpravy podle formulářů** – kdyby Play při kontrole něco chtěl doladit (např. text u oprávnění), doladím.

## Část C — 🧑 Co uděláš ty v Play konzoli (krok za krokem)

### 1. Vývojářský účet
- Jdi na **play.google.com/console**, přihlas se Google účtem, zvol **osobní** účet.
- Projdi **ověřením identity** (Google dnes vyžaduje ověření – doklad totožnosti, adresa; může trvat pár dní).
- Zaplať **registrační poplatek** (historicky jednorázově 25 USD – ověř aktuální částku v konzoli).

### 2. Založ aplikaci
- V konzoli **Create app** → název „GeoReminder", jazyk čeština, typ **App**, **Free** (zdarma).
- Odsouhlas prohlášení (že to není např. appka pro děti apod.).

### 3. Uzavřený test (12 testerů / 14 dní) — začni s tím HNED
- V konzoli **Testing → Closed testing** → vytvoř track, přidej **e-maily aspoň 12 testerů** (Gmail účty).
- Nahraj do něj první `.aab` (dodám ho).
- Testeři musí appku **přijmout přes odkaz a mít ji nainstalovanou 14 dní**. Čím dřív začneš, tím dřív budeš moct do produkce.

### 4. Privacy policy (zásady ochrany soukromí) — POVINNÉ
- Kvůli tomu, že appka používá polohu (a polohu na pozadí), Play **vyžaduje veřejnou URL se zásadami soukromí**.
- Nemusíš mít web: dá se použít generátor (např. termly, freeprivacypolicy) nebo prostá stránka (GitHub Pages, Google Sites). Musí popisovat, že appka používá polohu k hlídání míst, data drží v telefonu a neposílá je nikam (kromě volitelné zálohy do tvého Google účtu a hledání míst přes Photon/OpenStreetMap).
- URL vložíš v konzoli do **App content → Privacy policy**. *(Rád ti text zásad připravím, jen řekni.)*

### 5. Data safety formulář (bezpečnost dat) — POVINNÉ
- **App content → Data safety.** Vyplníš, co appka sbírá. Za GeoReminder pravdivě:
  - **Poloha (přesná + přibližná):** ANO, používá se pro funkci připomínek na místo. **Neshromažďuje se na server, nesdílí se.** Zpracovává se v zařízení.
  - **Žádné jméno/e-mail/kontakty/platby** appka nesbírá.
  - Data jsou **šifrovaná při přenosu** (hledání přes HTTPS) a uživatel si je může smazat (smazáním připomínek/appky).

### 6. Deklarace polohy na pozadí — POVINNÉ (kvůli geofencingu)
- Protože appka potřebuje `ACCESS_BACKGROUND_LOCATION` (aby připomínka přišla se zavřenou appkou), Play vyžaduje **zvláštní deklaraci**: prominentní vysvětlení uživateli + formulář, proč to appka potřebuje, a **často krátké video** ukazující ten scénář („připomeň mi, až budu u obchodu").
- Play navíc v roce 2026 pravidla polohy zpřísnil – v konzoli u deklarace uvidíš aktuální požadavky, drž se jich. *(Text zdůvodnění ti připravím.)*

### 7. Přesné budíky (USE_EXACT_ALARM)
- Play tohle oprávnění povoluje jen appkám typu **budík / kalendář / připomínky** – GeoReminder do kategorie spadá, ale při odeslání to **zdůvodni** (appka doručuje časové připomínky v přesný čas zvolený uživatelem). Pokud by dělal problém, mám v záloze variantu bez něj (uživatel by si oprávnění zapínal sám).

### 8. Podpis (Play App Signing)
- Při prvním uploadu Play nabídne **Play App Signing** – doporučuju přijmout (Google spravuje finální podpisový klíč, ty nahráváš „upload key" = náš stávající keystore). Keystore tedy **neztrať**.

### 9. Content rating (věkové hodnocení)
- **App content → Content rating** → vyplň dotazník (appka bez násilí/nevhodného obsahu → nízké hodnocení). Pár minut.

### 10. Store listing (jak appka vypadá v obchodě)
- **Main store listing:** krátký + dlouhý popis, **ikona 512×512**, **feature graphic 1024×500**, a **screenshoty** (aspoň 2–3 z telefonu). Ikonu i screenshoty umíme vyrobit, jen řekni.

### 11. Vydání
- Až projde uzavřený test (14 dní) a všechny formuláře jsou zelené: **Production → Create release** → nahraj `.aab` → odešli k revizi. Kontrola Googlem obvykle pár dní.

## Část D — 🤔 Rozhodnutí pro tebe

1. **Osobní vs. firemní účet?** Pro osobní appku doporučuju osobní (levnější), ale počítej s testem 12/14. Napiš, jestli chceš jít touhle cestou.
2. **Zapnout minifikaci teď?** Doporučuju: nech mě ji zapnout a pošlu ti build na otestování, než se dělá `.aab`. (Menší appka, ale chce to ověřit na telefonu.)
3. **Privacy policy** – chceš, abych ti připravil hotový text (stačí ho někam vyvěsit), nebo si ho vyřešíš sám?
4. **Store grafika** (ikona 512, feature graphic, screenshoty) – mám ti je vyrobit?

---

## Doporučené pořadí

1. Založ účet + ověř identitu (běží na pozadí pár dní).
2. Řekni mi „zapni minifikaci" → otestuješ build → sestavím `.aab`.
3. Založ appku, spusť **uzavřený test s 12 testery** (hodiny běží!).
4. Mezitím vyplň: privacy policy, Data safety, deklarace polohy, content rating, store listing.
5. Po 14 dnech testu → vydání do produkce → revize Googlem.

*Zdroje (ověřeno 21. 7. 2026): [Target API 35 requirement](https://support.google.com/googleplay/android-developer/answer/11926878), [Testování pro nové osobní účty (12/14)](https://support.google.com/googleplay/android-developer/answer/14151465), [Background location](https://support.google.com/googleplay/android-developer/answer/9799150), [Exact alarm policy](https://support.google.com/googleplay/android-developer/answer/16558241). Přesné UI konzole se může lišit – řiď se smyslem kroků.*
