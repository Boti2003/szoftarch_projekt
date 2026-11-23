# Identity, Gateway és Perembiztonság – Architektúrális Döntések (ADR)

Ez a dokumentum az A vertikum (Identity, Gateway és Perembiztonság) véglegesített tervezési döntéseit, a megvizsgált alternatívákat és a döntések mérnöki indoklását rögzíti.

---

## 1. Replay támadás és token-lopás kezelése

### Kontextus
A Refresh Token Rotáció (RTR) során minden tokenfrissítéskor a felhasznált token érvénytelenné válik (`revoked_at`), és mutat az új tokenre (`replaced_by_id`). Ha egy kliens már lecserélt tokent küld be, a rendszer Replay (újrajátszási) támadást észlel.

### Alternatívák
- **A) Családonkénti láncvisszavonás:** Csak az adott eszközhöz tartozó tokenlánc visszavonása a `replaced_by_id` fát bejárva. A többi eszköz (pl. mobiltelefon) aktív marad.
- **B) Teljes fiókszintű azonnali kizárás:** A felhasználó összes létező refresh tokenjének visszavonása minden eszközről (`revokeAllForUser`).

### Döntés és indoklás
A családonkénti láncvisszavonást (A opció) választottuk. 

Ha a támadó csak egy adott böngészőből szerzett meg egy tokent, a támadó kizárása mellett felesleges a felhasználó többi eszközét megzavarni. Ha a jelszó is kitudódott volna, a támadó már eleve jelszót tudna változtatni, ami nagyságrenddel súlyosabb probléma egy ellopott session tokennél. A fiókátvétel igazi védelme az, hogy a jelszócseréhez megköveteljük az aktuális jelszót, a jövőben pedig megvizsgáljuk a 2FA bevezetését. Emellett legitim jelszócsere esetén a rendszer automatikusan lefuttatja a teljes fiókszintű tiltást (`revokeAllForUser`), kizárva minden eszközt.

---

## 2. User-Agent ellenőrzés a tokenfrissítésnél

### Kontextus
A `refresh_tokens` táblában tároljuk a kliens HTTP `User-Agent` fejlécének SHA-256 lenyomatát (`user_agent_hash`).

### Alternatívák
- **A) Szigorú elutasítás:** Eltérő User-Agent hash esetén a kérés 401 Unauthorized hibával elutasításra kerül és a token visszavonódik.
- **B) Részleges egyezésvizsgálat:** Csak az OS és a böngészőcsalád egyezését vizsgáljuk, a pontos verziószámot figyelmen kívül hagyjuk.
- **C) Megengedő / csak naplózás:** Az eltérést figyelmeztetésként naplózzuk, de engedélyezzük a frissítést.

### Döntés és indoklás
A szigorú elutasítást (A opció) választottuk.

A cél a munkamenet-lopás aktív megakadályozása: ha egy mobilról kiadott tokent hirtelen egy asztali gépről vagy scriptből próbálnak beváltani, azt a rendszer azonnal blokkolja. A részleges (B) megoldás entrópiája túl alacsony, a felhasználók nagy része azonos kategóriákba esik (pl. Windows 11 + Chrome), így alig nyújtana védelmet a támadókkal szemben, miközben bonyolítaná a kódot. A csak naplózó (C) opció túl engedékeny, nem állítja meg az aktív támadót. Bár a böngészők frissülése módosíthatja a User-Agent szöveget, az új verzió csak böngésző-újraindításkor lép érvénybe; ilyenkor a memóriabeli access token amúgy is elvész, és a felhasználótól elvárható az újbóli bejelentkezés a fokozott biztonságért cserébe.

---

## 3. Szintszámítási modell

### Kontextus
A felhasználó által gyűjtött tapasztalati pontok (`total_points`) alapján meg kell határozni az aktuális szintet (`level`) és a következő szint küszöbét.

### Alternatívák
- **A) Lineáris modell:** Fix pontszám minden szinthez (pl. 100 pont/szint).
- **B) Progresszív gyökös modell:** A magasabb szintekhez egyre több pont kell: $\text{Level} = \lfloor \sqrt{\text{points} / C} \rfloor + 1$.
- **C) Küszöbtáblázat:** Külön táblában vagy kódban tárolt fix mérföldkövek.

### Döntés és indoklás
A progresszív gyökös modellt (B opció) választottuk ($C = 50$ konstanssal):

$$\text{Level} = \left\lfloor \sqrt{\frac{\text{total\_points}}{50}} \right\rfloor + 1$$

A képlet motiváló játékmechanikát ad: az 1. szintről a 2. szintre 50 pont eljutni (gyors kezdeti sikerélmény), a 3. szinthez 200, a 4. szinthez 450, az 5. szinthez 800 pont szükséges. Zárt matematikai formula, amelyet a backend közvetlenül a memóriában, adatbázis-lekérdezés nélkül ki tud számolni. A következő szinthez szükséges pontszám közvetlenül adódik:

$$\text{nextLevelPoints} = (\text{Level})^2 \times 50$$

---

## 4. Access Token érvénytelenítés kijelentkezéskor

### Kontextus
Kijelentkezéskor a Refresh Token azonnal törlődik a böngészőből és visszavonásra kerül az adatbázisban. Az állapotmentes RS256 JWT Access Token azonban a lejárati idejéig matematikailag érvényes marad.

### Alternatívák
- **A) Természetes lebegő lejárat:** Az access token a lejárati idejéig él, a kliens memóriájából törlődik, új tokent senki sem kérhet.
- **B) Gateway-szintű tiltólista:** A Gateway memóriájában tárolt token feketelista az azonnali érvénytelenítéshez.

### Döntés és indoklás
A természetes lebegő lejáratot (A opció) választottuk, de a biztonsági ablak minimalizálása érdekében **5 perces (300 másodperces) TTL-lel**.

Ez megőrzi a mikroszolgáltatások teljes állapotmentességét és horizontális skálázhatóságát, nincs szükség központi cache-re vagy szűk keresztmetszetet jelentő adatbázis-ellenőrzésre minden API hívásnál. Az 5 perces TTL 66%-kal csökkenti a kockázati ablakot a korábbi 15 perchez képest, miközben az 5 percenkénti csendes háttérfrissítés elhanyagolható hálózati terhelést jelent. A jövőre nézve feladat (TODO) egy elosztott, skálázható tiltólista-megoldás vizsgálata, amennyiben szigorúbb követelmények merülnének fel.

---

## 5. Gateway szerepköre és belső biztonság (Zero-Trust)

### Kontextus
A kérések az Nginx után a Spring Cloud Gateway-en keresztül jutnak el a belső mikroszolgáltatásokhoz (Identity, Learning, Social).

### Alternatívák
- **A) Edge Offloading:** A Gateway validálja a JWT-t, és megbízható fejléceket (`X-User-Id`, `X-User-Roles`) továbbít lefelé. A belső service-ek nem ellenőriznek tokent.
- **B) Zero-Trust Defense-in-Depth:** A Gateway továbbadja a Bearer tokent, és minden belső mikroszolgáltatás maga ellenőrzi az aláírást a publikus kulccsal.

### Döntés és indoklás
A Zero-Trust modellt (B opció) választottuk (egyeztetve a csapattársakkal).

A belső hálózatot sem tekintjük feltétel nélkül megbízhatónak. Ha a szolgáltatások vakon megbíznának egy `X-User-Id` fejlécben, egy rosszul konfigurált route vagy egy kompromittált belső pont fejléc-hamisítással (Header Spoofing) bármelyik felhasználó nevében felléphetne. Mivel az RS256 ellenőrzés lokális matematikai művelet, a szolgáltatásonkénti validáció nem okoz mérhető késleltetést, és a kontrollerekben közvetlenül használható a Spring `@AuthenticationPrincipal Jwt jwt` megoldása.

---

## 6. Regisztrációs bemeneti szabályok

- **Felhasználónév (Username):** `^[a-zA-Z0-9_]{3,30}$` (3–30 karakter, alfanumerikus és alulvonás). Biztonságos az URL-ekben, kizárja az injekciós támadásokat, és a kisbetűs B-tree indexszel gyorsan kereshető.
- **Jelszó (Password):** Minimum 8 karakter, tetszőleges karakterkészletből. Hashelése kötelezően BCrypt-tel történik (megfelelő munka-faktorral), ami hatékony védelmet nyújt a szivárgás esetén történő szótáralapú és szivárványtáblás támadások ellen.

---

## 7. Összefoglaló táblázat

| Terület | Választott megoldás | Részletek |
|---|---|---|
| Aláírási algoritmus | RS256 (Aszimmetrikus) | Privát kulcs az Identity-nél, publikus kulcs mindenhol |
| Token élettartamok | Rövid Access, hosszú Refresh | Access: 5 perc (300 s), Refresh: 7 nap (604800 s) |
| Kliensoldali tárolás | Memória + HttpOnly süti | Access token: JS memória; Refresh token: HttpOnly, Secure, SameSite=Strict |
| Adatbázis tárolás | SHA-256 lenyomat | A DB-ben nem tárolunk nyers refresh tokent |
| Replay támadás | Családonkénti lánctiltás | Csak az érintett eszköz záródik ki; jelszócserénél globális kiléptetés |
| User-Agent eltérés | Szigorú 401 elutasítás | Platformváltás azonnali blokkolása; újraindításkor re-login elfogadható |
| Szintszámítás | Progresszív gyökös modell | Level = floor(sqrt(points / 50)) + 1, állapotmentes CPU számítás |
| Kijelentkezés | Lebegő lejárat (5 perc) | Nincs központi állapot; elosztott blacklist skálázhatósága TODO |
| Belső hálózat | Zero-Trust | Minden belső service maga validálja a JWT-t |
