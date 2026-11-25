# Security Architektúrális Döntések

Ez a dokumentum az A vertikum (Identity, Gateway és Perembiztonság) véglegesített tervezési döntéseit, a megvizsgált alternatívákat és a döntések indoklását rögzíti.

## 1. Access- és refresh token alapú authentikáció (JWT)

A módszer előnye, hogy a hosszú élettartamú refresh token biztosítja, hogy egy eszközön ne kelljen gyakran bejelentkezni, a rövidtávú access token pedig egy JWT (access token, lejárat, userID aláírva) részeként tehermentesíti az identity service-t: a token lejártáig bármely service ellenőrizheti annak érvényességét, ha rendelkezik a az aláíró (identity service) publikus kulcsával. Így csak a token lejáratakor kell az identity service-t hívni.

### Kritériumok
- A refresh token:
  - nem kaphatja meg a kliensoldali JavaScript kód
  - hosszú távon érvényes (pl. több nap)
  - nem használható hozzáféréshez
  - visszavonható
  - csak vissza nem vont és le nem járt refresh tokennel vagy jelszavas bejelentkezéssel lehet létrehozni
  - láncolva van az elődökhöz, amikkel létre lett hozva (RT1 -> RT2 -> RT3)
- Az access token (és JWT):
  - rövid távon érvényes
  - az identity service kulcsával aláírt
  - új access token generálásakor új refresh token is generálódik

### Döntések

#### 1.1 Token replay
Minden tokenfrissítéskor a felhasznált token érvénytelenné válik, és mutat az új tokenre. Ha egy kliens már lecserélt tokent küld be, akkor feltételezzük, hogy replay támadás történt. Ilyenkor ezeket tehetjük:

- A) Családonkénti láncvisszavonás: Csak az adott tokenlánc visszavonása. (Jellemzően egy tokenlánc egy eszközt vagy böngészőt jelent)
- B) Teljes fiókszintű kizárás: A felhasználó összes létező refresh tokenjének visszavonása minden eszközről.

- Döntés és indoklás:
  - A családonkénti láncvisszavonást (A) választottuk. 
  - Ha a támadó csak egy adott böngészőből szerzett meg egy tokent, a támadó kizárása mellett felesleges a felhasználó többi eszközét megzavarni. Ha a jelszó is kitudódott volna, a támadó már eleve jelszót tudna változtatni, ami nagyságrenddel súlyosabb probléma egy ellopott session tokennél. A fiókátvétel igazi védelme az, hogy a jelszócseréhez megköveteljük az aktuális jelszót.


#### 1.2 User-Agent ellenőrzés a tokenfrissítésnél
A refresh tokenhez tároljuk a kliens HTTP `User-Agent` fejlécének SHA-256 hash-ét. Ha a kérést végző user agent eltér a tárolttól, akkor ezek az opciók lehetségesek:

- A) Szigorú elutasítás: Eltérő User-Agent hash esetén a kérés 401 Unauthorized hibával elutasításra kerül és a token visszavonódik.
- B) Részleges egyezésvizsgálat: Csak az OS és a böngészőcsalád egyezését vizsgáljuk, a pontos verziószámot figyelmen kívül hagyjuk.
- C) Csak naplózás: Az eltérést figyelmeztetésként naplózzuk, de engedélyezzük a kérést.

Döntés és indoklás:
A szigorú elutasítást (A) választottuk.

A cél a session-hijack megakadályozása: ha egy mobilról kiadott tokent hirtelen egy asztali gépről vagy scriptből próbálnak beváltani, azt a rendszer azonnal blokkolja. A részleges (B) megoldás entrópiája túl alacsony, a felhasználók nagy része azonos kategóriákba esik (pl. Windows 11 + Chrome), így alig nyújtana védelmet a  szemben, bonyolítaná a kódot. A csak naplózó (C) opció túl engedékeny. 

Bár a böngészők frissülése módosíthatja a User-Agent szöveget, az új verzió általában csak böngésző újraindításakor lép érvénybe; ilyenkor a memóriabeli access token amúgy is elvész, és a felhasználótól elvárható az újbóli bejelentkezés a fokozott biztonságért cserébe.

## 1.3 JWT ClaimsSet struktúra és mezők indoklása

Az Access Token a következő mezőket (claim-eket) tartalmazza:
- `iss` (Issuer): `szoftarch-nyelvtanulo` 
- `sub` (Subject): A felhasználó belső UUID azonosítója (`users.id`). Ez stabilan a user-hez köthető, mert nem változtatható.
- `iat` (Issued At): A kiadás időbélyege UTC-ben.
- `exp` (Expiration Time): A lejárati idő UTC-ben.
- `jti` (JWT ID): Véletlenszerű UUID, amely egyedivé teszi az adott tokent.
- `username`: A felhasználó login azonosítója.
- `roles`: A felhasználó szerepkörei (jelenleg nem használt)

Tudatosan kimaradó mezők:
- `displayName`: Nem stabil adat. Ha a felhasználó megváltoztatja a nevét, az aláírt token lejárati idejéig  elavult adatot hordozna.
- Jelszó, email, statisztikák, pontszám: Érzékeny vagy gyakran változó adatok, amelyek nem tartoznak egy autorizációs tokenbe.

## 1.4 Access Token érvénytelenítés kijelentkezéskor

### Kontextus
Kijelentkezéskor (explicit, pl. user megnyomja a kijelentkezés gombot) a Refresh Token azonnal törlődik a böngészőből és visszavonásra kerül az adatbázisban.

#### Rés
Mivel az access token validálása az identity service-től független, ezért a lejáratáig érvényes marad, még akkor is, ha megnyomtuk a kijelentkezés gombot. Emiatt fontos, hogy az access token rövid élettartamú legyen (pl. 5 perc). Fontos megjegyezni, hogy ilyenkor a refresh token már visszavont, tehát nem lehet meghosszabítani a hozzáférést ezen a résen túl.

Potenciális megoldás: a gateway egy tiltólistát tart fent az access tokenekre, amit az identity service frissít. Azért nem ezt választottuk, mert bonyolítaná a kódot és extra függőségeket alkotna az identity service és a gateway között.

## 1.5 Kliensoldali token-tárolás és átvitel (HttpOnly Set-Cookie és in-memory Bearer token)
Az identity service az auth kérésekre válaszként a Set-Cookie headerben beállítja a refresh tokent a következő tulajdonságokkal:
- Max-Age: a token lejárati ideje (ezt később 0-ra állítjuk, ha kijelentkezés történik)
- HttpOnly: megakadályozza, hogy a böngészőben futó JS hozzáférjen a tokenhez (XSS esetén védett)
- SameSite=Strict: A sütit csak akkor csatolja a HTTP kérésekhez, ha azok a saját szerverünk felé mennek
- Path=/api/v1/auth: A sütit csak akkor csatolja, ha az auth végpontra érkezik a kérés (a többi végpontnak nincs szüksége a refresh tokenre, csak az access tokenre)
Így a refresh token visszaküldése a böngésző feladata, nem a kliens kódé.

Az access token JWT-t a szerver a válasz JSON törzsében adja át, a kliens pedig kizárólag a memóriájában tárolja (soha nem kerül session- vagy local storage-ba), és a későbbi kérések fejlécében küldi el (Authorization: Bearer <token>).

## 1.6 Aszimmetrikus token-aláírás
A JWT-khez RS256-t használunk: RSA + sha256. Azért aszimmetrikus kulcsú titkosítást választottunk, mert így elég az identity service-nek rendelkeznie a titkos kulccsal.


## 1.7 Jelszócsere és az aktív munkamenetek visszavonása
A jelszó cseréjekor megköveteljük a korábbi jelszó helyes megadását. A jelszócsere utáni válaszban küldünk egy friss refresh és access tokent, így a jelszócsere után is zökkenőmentes a használat, de csak azon az eszközön amiről cseréltük a jelszót. Az összes korábbi jelszóhoz tartozó refresh token visszavonásra kerül, azaz az access tokenek lejártával minden egyéb eszközön újra be kell jelentkezni.

## 2. Gateway szerepköre és belső hálózat védelme
A kérések az Nginx reverse proxy után a Spring Cloud Gateway-en keresztül jutnak el a belső mikroszolgáltatásokhoz (Identity, Learning, Social). A következő megoldásokat mérlegeltük:

- A) Gateway szinten: A Gateway validálja a JWT-t, és HTTP fejléceket (`X-User-Id`, `X-User-Roles`) továbbít lefelé (ezekben a service-ek már megbízhatnak, hogy valóban az adott user küld be pl. feladatot). A belső service-ek nem ellenőriznek tokent.
- B) Defense-in-Depth: A Gateway továbbadja a Bearer tokent (JWT), és minden belső mikroszolgáltatás maga ellenőrzi az aláírást a publikus kulccsal.

### Döntés és indoklás
A B-opciót választottuk. A belső hálózatot nem tekintjük feltétel nélkül megbízhatónak. Ha a szolgáltatások vakon megbíznának egy `X-User-Id` fejlécben egy kompromittált belső pont hamis fejléccel bármelyik felhasználó nevében felléphetne.

### 2.1 Token-validáció a mikroszolgáltatásokban
Minden mikroszolgáltatás megkapja a jwt-k ellenőrzéséhez használt nyilvános kulcsot és ellenőrzi a digitális aláírás helyességét. Elelnőrzi, hogy a token nem járt-e le. Ellenőrzi, hogy a kiadó az általunk beállított érték-e.

Ha bármelyik feltétel sérül, a kérés azonnal 401 Unauthorized hibával elutasításra kerül, mielőtt a kontrollereket elérné.

#### Fontos
Ha egy adott kérés rendelkezik pl. userID mezővel, akkor nem szabad úgy tekinteni erre, mint a kérés kezdeményezőjére, mert ez bármi lehet, a JWT-ből kinyert userID viszont biztos, hogy az authentikált userhez tartozik.

### 2.2 Belső végpontok védelme
A gateway elutasítja a belső végpontok felé irányuló külső hívásokat.

#### 2.2.1 Belső API kulcs (X-Internal-Api-Key)
A belső szolgálatások közt egy X-Internal-Api-Key headerbe tett titkos kulcs szolgálja a védelmet. Ez nem véd belső támadó ellen, hiszen az potenciálisan le tudja hallgatni a belső kommunikációt és leolvasni a headert.

### 2.3 CORS konfiguráció és Credential-kezelés (Access-Control-Allow-Origin, Allow-Credentials)
A gateway alkalmazza ezeket:
 - Access-Control-Allow-Origin: Csak a mi címünkre lehet API hívást indítani
 - Access-Control-Allow-Credentials: true: A refresh token-nél előjövő HttpOnly működéséhez kell

## 3. Perembiztonság és HTTP biztonsági válaszfejlécek (Nginx Reverse Proxy)

### 3.1 MIME sniffing (X-Content-Type-Options: nosniff)
A nosniff beállítás megakadályozza a MIME-sniffinget: a böngésző szigorúan tiszteletben tartja a megadott Content-Type-ot, így nem próbálja meg pl. a feltöltött képeket vagy szövegeket kitalálni és szkriptként futtatni.

### 3.2 Clickjacking (X-Frame-Options: DENY)
Ennek köszönhetően nem lehet embeddelni egy másik oldalba a miénket.

### 3.3 Content-Security-Policy
Csak belső forrásokat engedélyezzük az erőforrások betöltését (minden 'self')

### 3.4 HTTPS és TLS termináció
Az nginx reverse proxy végzi a TLS terminációt, így minden külső kommunikáció HTTPS-en megy.

## 4. Regisztrációs követelmények

- Felhasználónév: `^[a-zA-Z0-9_-]{3,30}$` (3–30 karakter, alfanumerikus, alulvonás vagy kötőjel). Biztonságos az URL-ekben, kizárja a legtöbb injekciós támadást.
- Jelszó: 8–128 karakter, legalább egy számjeggyel és legalább egy speciális karakterrel (`^(?=.*[0-9])(?=.*[^a-zA-Z0-9\s]).+$`).

## 5. Pontjóváírás védelme
A pontok jóváírása csak a belső hálózatról lehetséges. Minden kérésnek tartalmaznia kell egy idempotency key-t, aminek az a lényege, hogy a kérés többszöri lefutása esetén (pl. újrapróbálás hálózati hiba miatt) se legyen dupla jóváírás.

## 6. Naplózás és megfigyelhetőség (Observability)
A logok szövege típusokat tartalmaz, hogy később majd könnyen lehessen szűrni őket.
- WARN (Biztonsági riasztások): Minden olyan esemény, ami lehetséges támadásra vagy anomáliára utal.
  - Aláíráshamisítás vagy manipulált token: `[SECURITY_ALERT: INVALID_SIGNATURE]`
  - Replay attack detektálása (korábban rotált refresh token újbóli beküldése): `[SECURITY_ALERT: REFRESH_TOKEN_REPLAY]`
  - User-Agent hash eltérése tokenfrissítéskor: `[SECURITY_ALERT: USER_AGENT_MISMATCH]`
  - Sikertelen bejelentkezési kísérlet rossz jelszóval: `[AUTH_FAILURE: BAD_CREDENTIALS]`
  - Érvénytelen kiadójú token elutasítása: `[SECURITY_ALERT: UNKNOWN_ISSUER]`
- ERROR (Rendszerhibák):
  - pl.: Hiányzó RSA privát kulcs token kiadásakor.
  - Váratlan belső szerverhibák és adatbázis-elérési hibák.
- DEBUG (Diagnosztika)

### 6.1 Szenzitív mezők
A logokba tilos érzékeny hitelesítő adatot írni:
- Nyers jelszó
- Nyers refresh token
- Nyers access token
- Privát kulcs

### 6.2 Traceability és elosztott tranzakció-nyomkövetés (X-Correlation-Id)
A Gateway generál egy `X-Correlation-Id` fejlécet, amit az SLF4J segítségével a hívási láncon végig beleteszünk a logokba, így könnyen nyomonkövethető egy-egy tranzakció.

## 7. DTO-k, Adatmodell és JPA-k

A publikus REST API végpontok és az adatbázis JPA entitásai között szigorú szétválasztást alkalmazunk Data Transfer Object-ek (DTO) segítségével.

Ez azért fontos, mert a JPA-k az adatbázissal való kommunikációt szolgálják, az adatmodell osztályai az üzleti logika végrehajtására használandók, a DTO-k pedig az adok kiküldésére/fogadására szolgálnak a REST interfészeken (így ezen területek bármelyike viszonylag függetlenül változhat). A szétválasztás miatt könnyen le lehet szűkíteni, hogy milyen adatok mehetnek ki (adatmodell -> DTO), így nem kerülnek ki szenzitív adatok (pl. email címek). Korlátozni lehet a bejövő adat formátumát is (DTO -> adatmodell vagy JPA), így biztosan nem tud egy kártékony kérés olyan mezőkez változtatni, amik nem relevánsak (pl.: a username patchelése nem kellene, hogy jelszó mezőt tudjon tartalmazni)


## 8. Frontend kliensoldali biztonsági megfontolások
A kliensoldalon az API hívásokat közös függvénnyel oldjuk meg, ami automatikusan kezeli az authentikációt. Ha lejárt az access token akkor rotálja és újrapróbálja a kérést. Ha a refresh token is lejárt, akkor bejelentkezteti a usert és visszanavigálja a korábbi URL-re.

### 8.1 Konkurens tokenfrissítés (race-condition)
Ha a kliensoldalon párhuzamosan több kérést indítanánk el, és az access token lejárt, akkor több kérés is meg próbálná újítani az access tokent ugyanazzal a refresh tokennel. Ezek közül az első sikeres lenne, de a többinél a rendszer refresh token replay-t észlelne és kijelentkeztetné a felhasználót.

Megoldás: a kliens oldalon egyszerre csak egy refresh kérést engedélyezünk és az új access tokent átadjuk a többi kérésnek, ahelyett, hogy lefuttatnánk őket.

### 8.2 React protected route
Ha nincs bejelentkezve a user, vagy lejárt a tokenje, akkor átirányítja a loginra és megjegyzi, hogy hol volt a user. A sikeres bejelentkezés után visszanavigál a kért oldalra. (Ez nem védelmi jelentőségű, inkább csak UX szempont.)

