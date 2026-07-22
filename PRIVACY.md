# Zásady ochrany soukromí – GeoReminder

**Poslední aktualizace: 22. 7. 2026**

Tyto zásady popisují, jak aplikace **GeoReminder** pro Android nakládá s vašimi daty. Aplikace je navržená tak, aby vaše data zůstávala ve vašem telefonu. Nemá žádné uživatelské účty, nesbírá data na server provozovatele, neobsahuje reklamy ani nástroje pro sledování.

Provozovatel aplikace: **Jenda** (kontakt: mcnegr@gmail.com).

## Jaká data aplikace zpracovává a kde zůstávají

- **Připomínky, oblíbená místa a jejich nastavení** (název, souřadnice a poloměr místa, čas, druh upozornění) se ukládají **pouze do soukromého úložiště aplikace ve vašem telefonu**. Neodesílají se nikam jinam.
- **Přílohy** (fotky, PDF a jiné soubory, které si k připomínce sami přidáte) se **zkopírují do soukromého úložiště aplikace** a zůstávají ve vašem telefonu.
- **Poloha** se používá k tomu, aby aplikace poznala, že jste dorazili na místo připomínky nebo z něj odjeli (tzv. geofencing). Toto vyhodnocuje operační systém Android přímo v zařízení. Aplikace vaši polohu **neukládá a neodesílá na žádný server provozovatele**.
- **Kalendář** aplikace **čte pouze na vaši výslovnou akci** (když si vyberete „Import z kalendáře"), a to jen kvůli zobrazení seznamu nadcházejících událostí, ze kterých si jednu vyberete. Aplikace kalendář needituje a jeho obsah nikam neodesílá.

## Kdy data opouštějí zařízení (služby třetích stran)

Aplikace nemá vlastní server, ale pro některé funkce využívá služby třetích stran. Předává se jen to, co je pro danou funkci nutné:

- **Mapy Google (Google Maps SDK for Android):** k vykreslení mapy při výběru místa. Google při tom může zpracovávat údaje o zařízení a poloze podle vlastních zásad ochrany soukromí Google.
- **Hledání míst – Photon (photon.komoot.io, data z OpenStreetMap):** když v aplikaci hledáte místo, odešle se **text vašeho dotazu** této službě, která vrátí odpovídající místa.
- **Převod adresy na souřadnice (geokódování):** při importu události z kalendáře (a jako záloha hledání) může aplikace použít geokódovací službu systému Android, které se předá **text adresy**.
- **Rozbalení odkazu z Map Google:** když do aplikace nasdílíte místo z Map Google, aplikace navštíví daný odkaz, aby z něj zjistila souřadnice.

Veškerá tato komunikace probíhá přes zabezpečené připojení (HTTPS).

## Zálohování

Pokud máte na telefonu zapnuté zálohování Androidu, systém může zálohovat vaše připomínky, oblíbená místa a přílohy do **vašeho vlastního účtu Google** (tzv. řízená záloha), aby přežily výměnu telefonu. Tuto zálohu spravuje Google podle vlastních zásad; provozovatel aplikace k ní nemá přístup. Zálohování si můžete v nastavení telefonu vypnout.

## Co aplikace NEdělá

- Nemá uživatelské účty ani přihlašování.
- Neobsahuje reklamy.
- Neobsahuje analytiku ani nástroje pro sledování chování.
- Neprodává ani nesdílí vaše data třetím stranám pro reklamní účely.

## Oprávnění a proč je aplikace používá

- **Poloha (přesná, přibližná a na pozadí / „Povolit vždy"):** hlídání míst připomínek i se zavřenou aplikací.
- **Notifikace:** zobrazení připomínek.
- **Přesné budíky:** doručení časových připomínek v přesný zvolený čas.
- **Spuštění po restartu:** obnovení hlídání po restartu telefonu.
- **Vyjmutí z optimalizace baterie:** spolehlivé doručení připomínek na pozadí (volitelné).
- **Internet:** zobrazení mapy a hledání míst.
- **Čtení kalendáře:** jednorázový import události do připomínky (jen na vaši akci).

## Vaše kontrola nad daty

Data můžete kdykoli odstranit – smazáním jednotlivých připomínek a příloh v aplikaci, vymazáním dat aplikace v nastavení telefonu, nebo odinstalací aplikace. Protože aplikace neukládá žádná vaše data na server provozovatele, odinstalací se vaše data z aplikace odstraní.

## Děti

Aplikace není určena dětem a záměrně od nich neshromažďuje žádná data.

## Změny těchto zásad

Tyto zásady můžeme čas od času aktualizovat. Aktuální verze je vždy dostupná na této stránce s uvedeným datem poslední aktualizace.

## Kontakt

Máte-li dotaz k ochraně soukromí v aplikaci GeoReminder, napište na: **mcnegr@gmail.com**.
