# Security Architektúrális Döntések

Ez a dokumentum az A vertikum (Identity, Gateway és Perembiztonság) véglegesített tervezési döntéseit, a megvizsgált alternatívákat és a döntések indoklását rögzíti.

TODO jelszócsere

## 1. Access- és refresh token alapú authentikáció (JWT)

A módszer előnye, hogy a hosszú élettartamú refresh token biztosítja, hogy egy eszközön ne kelljen gyakran bejelentkezni, a rövidtávú access token pedig egy JWT (access token, lejárat, userID aláírva) részeként tehermentesíti az identity service-t: a token lejártáig bármely service ellenőrizheti annak érvényességét, ha rendelkezik a az aláíró (identity service) publikus kulcsával. Így csak a token lejáratakor kell az identity service-t hívni.

### Kritériumok
- A refresh token:
  - nem kaphatja meg a kliensoldali JavaScript kód (TODO)
  - hosszú távon érvényes (pl. több nap)
  - nem használható hozzáféréshez
  - visszavonható
  - csak vissza nem vont és le nem járt refresh tokennel vagy jelszavas bejelentkezéssel lehet létrehozni
  - láncolva van az elődökhöz, amikkel létre lett hozva (RT1 -> RT2 -> RT2)
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
- `roles`: A felhasználó szerepkörei (pl. `["ROLE_USER"]`). A Spring Security `GrantedAuthority` mapping-jához szükséges (`hasRole('USER')`). TODO????

Tudatosan kimaradó mezők:
- `displayName`: Nem stabil adat. Ha a felhasználó megváltoztatja a nevét, az aláírt token lejárati idejéig  elavult adatot hordozna.
- Jelszó, email, statisztikák, pontszám: Érzékeny vagy gyakran változó adatok, amelyek nem tartoznak egy autorizációs tokenbe.

## 1.4 Access Token érvénytelenítés kijelentkezéskor

### Kontextus
Kijelentkezéskor (explicit, pl. user megnyomja a kijelentkezés gombot) a Refresh Token azonnal törlődik a böngészőből és visszavonásra kerül az adatbázisban.

#### Rés
Mivel az acces token validálása az identity service-től független, ezért a lejáratáig érvényes marad, még akkor is, ha megnyomtuk a kijelentkezés gombot. Emiatt fontos, hogy az access token rövid élettartamú legyen (pl. 5 perc). Fontos megjegyezni, hogy ilyenkor a refresh token már visszavont, tehát nem lehet meghosszabítani a hozzáférést ezen a résen túl.

Potenciális megoldás: a gateway egy tiltólistát tart fent az access tokenekre, amit az identity service frissít. Azért nem ezt választottuk, mert bonyolítaná a kódot és extra függőségeket alkotna az identity service és a gateway között.

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
- A belső szolgálatások közt egy X-Internal-Api-Key headerbe tett titkos kulcs szolgálja a védelmet. Ez nem véd belső támadó ellen, hiszen az le tudja olvasni a kulcsot. (TODO???)
- A gateway elutasítja a belső végpontok felé irányuló külső hívásokat.

## 3. Regisztrációs követelmények

- Felhasználónév: `^[a-zA-Z0-9_-]{3,30}$` (3–30 karakter, alfanumerikus, alulvonás vagy kötőjel). Biztonságos az URL-ekben, kizárja a legtöbb injekciós támadást.
- Jelszó:** 8–128 karakter, legalább egy számjeggyel és legalább egy speciális karakterrel (`^(?=.*[0-9])(?=.*[^a-zA-Z0-9\s]).+$`).

### 4. Pontjóváírás védelme
A pontok jóváírása csak a belső hálózatról lehetséges. Minden kérésnek tartalmaznia kell egy idempotency key-t, aminek az a lényege, hogy a kérés többszöri lefutása esetén (pl. újrapróbálás hálózati hiba miatt) se legyen dupla jóváírás.

### 5. Naplózás
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

### 5.1. Szenzitív mezők
A logokba tilos érzékeny hitelesítő adatot írni:
- Nyers jelszó
- Nyers refresh token
- Nyers access token
- Privát kulcs

### 5.2 Traceability 
A Gateway generál egy `X-Correlation-Id` fejlécet, amit az SLJF segítségével a hívási láncon végig beleteszünk a logokba, így könnyen nyomonkövethető egy-egy tranzakció.

## 11. DTO réteg és adatmodell-szétválasztás (Decoupling)

A publikus REST API végpontok és az adatbázis JPA entitásai (`User`, `UserStats`, `RefreshToken`) között szigorú szétválasztást alkalmazunk Data Transfer Object-ek (DTO) segítségével.

### 11.1. Mérnöki indoklás
1. **Mass-Assignment (Over-Posting) elleni védelem:** Ha a kontroller közvetlenül JPA entitást fogadna a kérés törzséből, egy rosszindulatú kliens manipulálhatná azokat a belső mezőket is, amelyeket a kliens nem állíthatna be (pl. `status`, `id`, `createdAt`, `version`). A DTO-k kizárólag a kliens által legitim módon beküldhető mezőket tartalmazzák.
2. **Érzékeny adatok szivárgásának megelőzése:** A JPA entitások olyan érzékeny belső mezőket tartalmaznak, mint a `passwordHash`. A DTO réteg (pl. `UserSummaryDto`) garantálja, hogy véletlenül se kerülhessen ki belső jelszóhash, refresh token hash vagy relációs adat a hálózatra.
3. **API szerződés és belső séma független evolúciója:** Az adatbázis sémája normalizálható, indexelhető, particionálható vagy refaktorálható anélkül, hogy a kliensek felé nyújtott publikus OpenAPI specifikáció és HTTP szerződés megsérülne.
4. **Immutabilitás Java Record-okkal:** Minden DTO modern Java `record` típusként valósul meg. Ez garantálja a szálbiztos megváltoztathatatlanságot (immutability), automatikusan biztosítja az accessort, konstruktort, `equals()`, `hashCode()` és `toString()` metódusokat, valamint tiszta integrációt ad a Jackson JSON szerializációval.

---

## 12. ACID garanciák megvalósítása a rendszerben

A mikroszolgáltatás relációs adatbázis-műveleteinél a tranzakciós megbízhatóságot a következő rétegek garantálják:

- **Atomicity (Atomiság):** A Spring keretrendszer `@Transactional` annotációja (AOP proxy) vezérli. Bármely kezeletlen `RuntimeException` esetén a Spring automatikusan `ROLLBACK` parancsot küld az adatbázisnak. Például a `register(...)` metódusban a `User` és a kezdeti `UserStats` létrehozása egyetlen atomi egységet alkot: ha a statisztika mentése meghiúsulna, a felhasználó rekordja sem marad perzisztálva.
- **Consistency (Konzisztencia):** 
  - Adatbázisszintű integritási kényszerek: Foreign key kapcsolatok (`user_id`), egyedi indexek (`email`, `username`, `idempotency_key`, `token_hash`).
  - Alkalmazásszintű validáció: Jakarta Bean Validation (`@Valid`, `@NotBlank`, `@Email`, `@Pattern`) meggátolja, hogy érvénytelen vagy hiányos állapotú entitások mentésre kerüljenek.
- **Isolation (Izoláció):** 
  - Alapértelmezett `READ_COMMITTED` tranzakciós izoláció az adatbázisban, megelőzve a piszkos olvasást (dirty read).
  - Párhuzamos módosítások védelme: A `UserStats` entitás optimista zárolással (`@Version Long version`) védett. Párhuzamos pontjóváírás esetén az ütköző tranzakció `OptimisticLockingFailureException`-nel meghiúsul ahelyett, hogy felülírná a másik folyamat pontszámát (Lost Update megelőzése).
  - Osztályszintű `@Transactional(readOnly = true)`: Olvasási műveleteknél a Hibernate `MANUAL` flush módra vált és kihagyja az entitás-snapshotok összehasonlítását, csökkentve az erőforrás-használatot.
- **Durability (Tartósság):** A PostgreSQL relációs motor Write-Ahead Logging (WAL) mechanizmusa és a Docker Compose perzisztens kötetei biztosítják, hogy a jóváhagyott tranzakciók rendszerösszeomlás esetén is megmaradjanak.

---

## 13. Bejövő kérések feldolgozási lánca (Request Processing Lifecycle)

Amikor egy kliens HTTP kérést küld a REST interfészre (pl. `POST /api/v1/auth/login`), a hívás az alábbi ellenőrzési és transzformációs lépcsőkön megy keresztül:

1. **Servlet Container / Ingress:** A Tomcat szálkészlete fogadja a beérkező TCP/HTTP kérést.
2. **DispatcherServlet Routing:** A Spring központi vezérlője a HandlerMapping alapján megkeresi a cél kontroller metódust az `@RequestMapping("/api/v1/auth")` és `@PostMapping("/login")` illeszkedése szerint.
3. **JSON Deszerializáció (`@RequestBody`):** A Spring a regisztrált `HttpMessageConverter` (Jackson) segítségével a kérés nyers JSON törzsét beolvassa és példányosítja a DTO record-ot (`LoginRequest`).
4. **Bemeneti validáció (`@Valid`):** A Jakarta Bean Validation motor ellenőrzi a DTO-n lévő kényszereket (`@NotBlank`, `@Email`, stb.). Ha bármelyik mező hibás, a feldolgozás nem lép be a kontroller metódusába, hanem azonnal kiváltódik a `MethodArgumentNotValidException`.
5. **Környezeti metaadatok injektálása:** A kontroller paraméterlistáján lévő `HttpServletRequest` objektumon keresztül a kontroller lekéri a biztonsági ujjlenyomathoz szükséges HTTP fejléceket (`User-Agent`) és a hálózati IP címet.
6. **Alkalmazási szolgáltatás (`AuthService`):** A kontroller továbbadja a DTO-t az üzleti logikának, belépve a Spring `@Transactional` tranzakciós hatókörébe.

---

## 14. Kivételkezelési lánc (Exception Lifecycle) és RFC 9457 Problem Details

A rendszer deklaratív, központosított hibakezelést használ, így a kontrollerek és szolgáltatások mentesek a redundáns `try-catch` blokkoktól:

1. **Hiba fellépése:** Az üzleti logikában nem ellenőrzött kivétel váltódik ki (pl. `throw new ConflictException("Email is already registered: ...")`).
2. **Tranzakciós visszagörgetés:** A Spring tranzakciókezelő interceptora észleli az unchecked exception-t, kiadja a DB `ROLLBACK`-et, majd továbbengedi a kivételt.
3. **Buborékolás:** A kivétel átlép a kontrolleren és eléri a `DispatcherServlet`-et.
4. **Globális elfogás (`@RestControllerAdvice`):** A Spring a `HandlerExceptionResolverComposite` révén meghívja a [GlobalExceptionHandler](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/api/GlobalExceptionHandler.java) komponenst.
5. **Megfelelő handler kiválasztása:** A Spring az annotáció alapján kiválasztja a specifikus metódust (pl. `@ExceptionHandler(ConflictException.class)`).
6. **Biztonsági audit naplózás:** A hibakezelő lefutásakor strukturált biztonsági logbejegyzés képződik (pl. `[AUTH_FAILURE: CONFLICT]` vagy `[REQUEST_VALIDATION_FAILED]`).
7. **RFC 9457 Problem Details generálás:** A handler összeállítja a szabványos `ProblemDetails` rekordot (`type`, `title`, `status`, `detail`, `instance`, `code`, opcionálisan `fieldErrors`).
8. **JSON formázás (`@JsonInclude(NON_NULL)`):** A Jackson szerializáció során a `NON_NULL` beállítás biztosítja, hogy a nem releváns mezők (pl. konfliktus esetén a `fieldErrors` és a `traceId`) kimaradjanak a válaszból.
9. **Kliens válasz:** A szerver `application/problem+json` Content-Type fejléc mellett adja vissza a szabványos hibaobjektumot.

---

## 15. Belső mikroszolgáltatás-kommunikáció és pontjóváírási idempotencia

A más mikroszolgáltatásokból (pl. Kurzus/Feladat vagy Játék szerviz) érkező belső kérések kiszolgálása a `/internal/v1/**` végpontokon keresztül valósul meg.

### 15.1. Belső API védelem (`X-Internal-Api-Key`)
- **Kontextus:** A belső végpontok nem felhasználói Bearer JWT-t várnak, hanem a mikroszolgáltatások közötti dedikált kulcsot.
- **Megvalósítás:** Az `InternalApiKeyFilter` (`OncePerRequestFilter`) a Spring Security szűrőláncának elején ellenőrzi az `X-Internal-Api-Key` fejlécet.
- **Időzítéses támadások elleni védelem:** A kulcs összehasonlítása kötelezően konstans idejű algoritmussal történik (`MessageDigest.isEqual`), megakadályozva, hogy a támadó byte-onkénti válaszidő-méréssel fejtse vissza a belső kulcsot.
- **Hibaeset:** Hiányzó vagy hibás kulcs esetén `[SECURITY_ALERT: INTERNAL_API_KEY_INVALID]` audit napló képződik és 401 Unauthorized RFC 9457 ProblemDetails válasz érkezik.

### 15.2. Idempotencia garanciák a pontjóváírásnál
- **Kontextus:** Hálózati szakadás, timeout vagy újrapróbálkozási vihar (retry storm) esetén ugyanaz a pontjóváírási kérés többször is megérkezhet a Learning szerviztől.
- **Adatbázisszintű garancia:** A `point_transactions` tábla `idempotency_key` oszlopa egyedi (`UNIQUE`) megszorítással védett.
- **Kezelés:** 
  1. Ha egy már feldolgozott kulccsal érkezik kérés, a `PointService` nem hajt végre újabb jóváírást, és nem dob adatbázis-hibát, hanem azonnal visszaadja az eredeti tranzakció eredményét és a felhasználó aktuális statisztikáját `duplicate: true` jelzéssel.
  2. Ha egy már létező idempotencia-kulcsot egy másik felhasználó azonosítójával próbálnak felhasználni, a rendszer 409 Conflict hibát dob (`"Idempotency key already used for another user"`).

### 15.3. Szintszámítás és DRY architektúra (`LevelCalculator`)
- A progresszív gyökös szintszámítás és a következő szint küszöbértékének kalkulációja dedikált, tiszta segédosztályba (`LevelCalculator`) került.
- Mind a `UserService`, mind a `PointService` ezt az egyetlen forrást használja, kizárva a számítási képlet kódduplikációját.
- A számítás $O(1)$ idő- és tárigényű, közvetlenül memóriában fut le.

### 15.4. Batch statisztika lekérdezés (`public-stats:batch`)
- A többi mikroszolgáltatás számára biztosított csoportos statisztika-lekérdezés (`POST /internal/v1/users/public-stats:batch`) a `userStatsRepository.findAllWithUserByUserIds(...)` metódust használja.
- A `JOIN FETCH s.user` biztosítja, hogy a felhasználói profilok és a játékstatisztikák egyetlen SQL lekérdezéssel jöjjenek fel, teljesen kiküszöbölve az $N+1$ lekérdezési anomáliát.
- A válasz a már meglévő `PublicProfileDto`-t használja újra (DRY alapelv).

---

## 16. Globális Ranglista és Felhasználói Helyezés (Phase 7)

A gamifikált ranglista megjelenítését a `GET /api/v1/leaderboard` és `GET /api/v1/leaderboard/me` végpontok szolgálják ki.

### 16.1. Lekérdezési hatékonyság és N+1 anomália elkerülése
- A `findLeaderboard(Pageable pageable)` lekérdezés a `UserStats` és `User` rekordokat `JOIN FETCH` kapcsolattal kéri le, pontszám szerint csökkenő, elért időpont szerint növekvő sorrendben (`ORDER BY s.totalPoints DESC, s.updatedAt ASC`).
- A lapozás (pagination) sor-számlálásához dedikált `countQuery = "SELECT COUNT(s) FROM UserStats s"` van definiálva, elkerülve a felesleges join-okat a számláló lekérdezésben.

### 16.2. Optimális saját helyezés (myRank) meghatározás
- A frontend felhasználói élményhez elengedhetetlen, hogy a felhasználó akkor is lássa a saját pozícióját, ha épp az 1000. helyen áll, miközben az első oldalt (top 20) böngészi:
  1. **In-Page egyezés:** Ha a bejelentkezett felhasználó benne van az aktuálisan lekért oldalban, a rendszer a meglévő lista alapján állítja elő a `myRank` objektumot ($0$ extra adatbázis hívás).
  2. **Out-of-Page kalkuláció:** Ha a felhasználó nem szerepel az adott oldalon, a rendszer a `calculateRank(points)` lekérdezést hívja meg (`COUNT(s) + 1 WHERE totalPoints > :points`), amely a `total_points` B-tree index segítségével közvetlenül, milliszekundumos nagyságrendben kiszámítja a pontos rangot.

### 16.3. Redundanciamentes (DRY) adatstruktúra
- A ranglista tételeit és az egyéni pozíciót egyaránt az immutábilis [LeaderboardEntryDto](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/api/dto/LeaderboardEntryDto.java) rekord írja le (`rank`, `userId`, `username`, `displayName`, `level`, `totalPoints`), minimalizálva az osztályok számát és megelőzve a felesleges DTO duplikációt.

---

## 17. API Gateway Peremvédelem és Elosztott Nyomkövetés (Phase 8)

Az architektúra központi belépési pontját a Spring Cloud Gateway (WebFlux) és az Nginx TLS termináció alkotja.

### 17.1. Elosztott nyomkövetés (`CorrelationIdFilter`)
- **Kontextus:** A mikroszolgáltatás-hívási láncban (Nginx $\rightarrow$ Gateway $\rightarrow$ Identity / Learning / Social $\rightarrow$ Database) a hibakeresés és az auditálhatóság megköveteli a kérések egyértelmű azonosítását.
- **Megvalósítás:** A [CorrelationIdFilter](file:///home/istvan/szoftarch_projekt/gateway/src/main/java/hu/bme/szoftarch/gateway/CorrelationIdFilter.java) a Spring WebFlux szűrőlánc legelején (`Ordered.HIGHEST_PRECEDENCE`) vizsgálja az `X-Correlation-Id` fejlécet.
  - Ha a kliens küldött azonosítót, a rendszer változatlanul megőrzi és továbbadja downstream irányba.
  - Ha hiányzik, új UUID-t generál.
- **Kétirányú rögzítés:** A szűrő ráfűzi az azonosítót a belső szolgáltatások felé induló kérésre, valamint a `beforeCommit` hook segítségével a kliensnek visszaküldött válasz fejlécébe is bejegyzi.
- **Hibakezelési integráció:** Hiba esetén a kliens válaszában (RFC 9457 `ProblemDetails.traceId`) megjelenő azonosító alapján a fejlesztő azonnal meg tudja keresni az érintett belső mikroszolgáltatás logjait.

### 17.2. Peremszintű belső útvonal-védelem (Defense-in-Depth)
- A mikroszolgáltatások belső végpontjai (`/internal/**`) kizárólag a Docker belső hálózatán kommunikáló más szervizek számára fenntartottak.
- A Gateway útválasztási konfigurációja (`application.yml`) deklaratív módon kizárólag a publikus végpontokat route-olja a szolgáltatások felé:
  - `identity`: `/api/v1/auth/**`, `/api/v1/users/**`, `/api/v1/leaderboard/**`, `/api/v1/leaderboards/**`.
- Bármilyen `/internal/**` próbálkozás a Gateway szintjén automatikusan `404 Not Found` hibával zárul, mielőtt egyetlen belső szervizhez vagy adatbázishoz elérne.

---

## 18. Frontend Kliensoldali Biztonsági Modell és Felület (Phase 9)

A React 19 SPA felület (TypeScript, Material UI v6) megvalósítása a minimális kódbázis és a szigorú kliensoldali perembiztonság elveit követi.

### 18.1. In-memory Access Token tárolás (XSS védelem)
- Az Access Token szándékosan **NEM** kerül mentésre a böngésző `localStorage` vagy `sessionStorage` tárhelyébe, mivel azok tartalma tetszőleges JavaScript kód (pl. egy XSS sérülékenység) által kinyerhető lenne.
- Ehelyett a token kizárólag a JavaScript futási memóriában (a kliens modulváltozójában) él.
- A perzisztens munkamenetet a szigorúan `HttpOnly`, `SameSite=Strict` módon védett Refresh Token cookie biztosítja, amelyhez a JavaScript kód nem férhet hozzá.

### 18.2. Csendes tokenmegújítás és konkurens hívásdeduplikáció (Silent Refresh Mutex)
- Az 5 perces élettartamú Access Token lejárata transzparens a felhasználó számára:
- Ha egy kérés `401 Unauthorized` választ kap, az [apiFetch](file:///home/istvan/szoftarch_projekt/frontend/src/api/client.ts) kliens interceptor a háttérben automatikusan meghívja a `POST /api/v1/auth/refresh` végpontot.
- **Konkurens refresh védelem:** Ha a felületen egyszerre több kérés (pl. párhuzamos lekérések) kap 401-et, a kliens egy deduplikációs ígéret (`refreshPromise` mutex) mögé szervezi őket. Ez garantálja, hogy pontosan egyetlen `POST /auth/refresh` fut le. Ez kulcsfontosságú perembiztonsági védelem, mert megelőzi a párhuzamos tokenrotációt, amely a backend Refresh Token Rotation (RTR) logikáját tévesen token reuse támadásként aktiválná.
- Sikeres frissítés után beállítja az új memóriabeli tokent, és az összes elakadt kérést automatikusan és észrevétlenül újrapróbálja az új tokennel.

### 18.3. Minimalista állapotkezelés és védett útvonalak
- Nehézsúlyú külső könyvtárak (Redux, Zustand) nélkül, egyetlen tiszta [AuthContext](file:///home/istvan/szoftarch_projekt/frontend/src/features/auth/AuthContext.tsx) szolgáltatja a bejelentkezési állapotot (`useAuth`).
- A [ProtectedRoute](file:///home/istvan/szoftarch_projekt/frontend/src/features/auth/ProtectedRoute.tsx) komponens a nem autentikált látogatókat a `/login` oldalra irányítja a védett útvonalakról (`/profile`, `/leaderboard`).

### 18.4. Gamifikációs UI és dinamikus haladás
- A [ProfilePage](file:///home/istvan/szoftarch_projekt/frontend/src/features/profile/ProfilePage.tsx) dinamikus Material UI `LinearProgress` csíkon mutatja a következő szinthez szükséges XP haladást.
- A [LeaderboardPage](file:///home/istvan/szoftarch_projekt/frontend/src/features/leaderboard/LeaderboardPage.tsx) az első három helyezettet vizuális jelzéssel (🥇, 🥈, 🥉) tünteti fel, és kiemeli a bejelentkezett felhasználó saját pozícióját (`myRank`).

### 18.5. Eseményvezérelt Auth Lejárat és Return URL minta (Observer Minta)
- Ha a refresh token érvénytelen (lejárt a 7 nap vagy visszavonásra került az RTR lánc), a csendes frissítés elhasal.
- A [client.ts](file:///home/istvan/szoftarch_projekt/frontend/src/api/client.ts) az `onUnauthorized` callbacken keresztül közvetlenül értesíti az [AuthContext](file:///home/istvan/szoftarch_projekt/frontend/src/features/auth/AuthContext.tsx)-et (`setUser(null)`).
- A [ProtectedRoute](file:///home/istvan/szoftarch_projekt/frontend/src/features/auth/ProtectedRoute.tsx) reaktívan észleli az állapot megszűnését, elmenti a felhasználó aktuális útvonalát (`state: { from: location }`), és SPA módon a `/login` oldalra navigálja a felhasználót.
- A [LoginPage](file:///home/istvan/szoftarch_projekt/frontend/src/features/auth/LoginPage.tsx) és a [RegisterPage](file:///home/istvan/szoftarch_projekt/frontend/src/features/auth/RegisterPage.tsx) sikeres belépés után automatikusan a `from` útvonalra navigál vissza (alapértelmezetten `/profile`), így a felhasználó közvetlenül ott folytathatja a munkát, ahol a munkamenet lejárata előtt járt.
