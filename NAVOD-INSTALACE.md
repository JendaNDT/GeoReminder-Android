# GeoReminder – instalace do Android telefonu

## 1. Dostaň APK do telefonu

Vyber si nejpohodlnější cestu:

- **Google Drive / Disk:** nahraj `GeoReminder.apk` na Disk, v telefonu ho otevři z appky Disk.
- **Kabel:** připoj telefon k Macu (na Macu je potřeba utilita Android File Transfer nebo OpenMTP), zkopíruj APK do složky Stažené.
- **Zpráva sobě:** pošli si soubor mailem, přes Messenger/WhatsApp „sám sobě" apod.

## 2. Nainstaluj

1. V telefonu ťukni na soubor `GeoReminder.apk` (najdeš ho ve Staženém, nebo rovnou z Disku).
2. Telefon se zeptá, jestli povolit instalaci z tohoto zdroje → **Povolit** (jednorázové nastavení pro danou appku, např. pro Disk nebo Soubory).
3. Ťukni **Instalovat**. Když Play Protect vyskočí s dotazem, zvol **Přesto nainstalovat** – appka není z Obchodu Play, je podepsaná tvým vlastním klíčem.

## 3. První spuštění – na co odkliknout

Appka tě provede průvodcem (stejným jako na iPhonu). Po „Povolit a začít" přijdou systémové dotazy **v tomhle pořadí**:

1. **Notifikace** → Povolit
2. **Poloha** → „Při používání aplikace" (a ideálně **Přesná** poloha zapnutá)
3. **Poloha na pozadí** → otevře se obrazovka nastavení, tam zvol **„Povolit vždy"** ← tohle je na Androidu klíčové, bez toho připomínky na místa nepřijdou se zavřenou appkou!

Kdybys něco odklikl špatně, nevadí – appka to pozná a ukáže oranžový banner s tlačítkem přímo do Nastavení.

## 4. Widget a zástupci

- **Widget:** podrž prst na ploše → Widgety → GeoReminder → **Nejbližší připomínky** → přetáhni na plochu. Jde roztáhnout do šířky (pak ukazuje 3 řádky).
- **Rychlé akce:** podrž prst na ikoně appky → „Připomínka na čas" / „Připomínka na místo".

## 5. Jak testovat

- **Časová připomínka:** vytvoř připomínku Na čas za 2 minuty a zamkni telefon – notifikace má přijít přesně, s tlačítky **Hotovo** a **Odložit o hodinu** (rozbal ji podržením/šipkou).
- **Připomínka na místo:** poloměr nech aspoň **150 m**. Detekce příjezdu může mít zpoždění pár desítek sekund až jednotek minut – to je záměr Androidu (šetření baterie), ne chyba.
- **Navigovat:** u připomínky na místo najdeš tlačítko „Navigovat" v jejím detailu i přímo na notifikaci – otevře navigaci do cíle (Google Maps, jinak výběr mapové appky).
- **Hlasité čtení a Seskupení** zapneš v **Nastavení (⚙️ vpravo nahoře) → Funkce** (obojí je výchozí vypnuté). Hlasité čtení ti připomínku po spuštění přečte nahlas; seskupení sloučí víc připomínek na jednom místě do jednoho souhrnného upozornění.
- **Přílohy:** v detailu připomínky je sekce **Přílohy → Přidat přílohu** (foto, PDF…). Kopírují se do appky, takže přežijí i přeinstalaci.
- **Import z kalendáře:** ťukni na tlačítko kalendáře nahoře v seznamu; při prvním použití appka požádá o přístup ke kalendáři, pak vybereš událost a udělá se z ní připomínka.
- **Po restartu telefonu** se všechno hlídání samo obnoví.

## 6. Kdyby notifikace nechodily

Někteří výrobci (hlavně Xiaomi, Huawei, Honor, OnePlus) agresivně zabíjejí appky na pozadí. Pomůže:
Nastavení → Aplikace → GeoReminder → **Baterie → Bez omezení** (nebo „Neoptimalizovat").

## 7. Aktualizace

Novou verzi APK stačí nainstalovat **přes** stávající – data (připomínky, oblíbená místa) zůstanou zachovaná. Funguje to díky podpisovému klíči uloženému v projektu, tak ho neztrať. 🙂
