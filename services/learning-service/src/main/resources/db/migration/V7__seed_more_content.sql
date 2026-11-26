-- =========================================================================
-- V7 seed bővítés (U9 folytatása). Két dolgot ad hozzá, PUSZTÁN additívan a
-- V6 fölé (a V1..V6 változatlan marad):
--
--   1) ANGOL B1-B2 bővítés: 5 ÚJ angol lecke (order_index 5..9) a meglévő
--      angol nyelvhez ('00000000-0000-0000-0000-0000000000e0'). Témák:
--      phrasal verbs, idiómák, present perfect vs past simple, feltételes
--      mondatok (conditionals), vonzatos igék (dependent prepositions).
--      A nehézség MEDIUM/HARD túlsúllyal, az elmélet tartalmasabb a kezdő
--      leckéknél (több bekezdés + példák).
--
--   2) NÉMET új nyelv (code 'de', fix UUID) + 3 német lecke (order_index
--      1..3), kezdő->A2 szinten.
--
-- Mindegyik lecke tartja az U9 seed-invariánsokat: leckénként 5 automatikus
-- feladat (mind az 5 auto típusból egy-egy) + 2 AI feladat. Az auto feladatok
-- required_for_completion=TRUE és duel_eligible=TRUE; az AI feladatok
-- required_for_completion=FALSE és duel_eligible=FALSE.
--
-- Az elmélet és a promptok MAGYARUL magyaráznak, a gyakorolt tartalom a
-- lecke nyelvén (angol / német). A JSONB payload/solution a U2 recordok
-- mezőneveihez illeszkedik (a V6 a működő minta), hogy az
-- ExerciseContentCodec deszerializálja és az U3 evaluator a solution szerinti
-- helyes válaszra correct=true / score_ratio=1.0 eredményt adjon.
--
-- max_points = floor(alappont x nehézségi szorzó) (TERV 5.1 / U3):
--   alappont MC 10 / TF 8 / MATCHING 12 / ODD_ONE_OUT 10 / ERROR_FINDING 12
--            TRANSLATION 15 / AI_SCENARIO 30 (AI, fix)
--   szorzó   EASY 1.0 / MEDIUM 1.5 / HARD 2.0
--   -> MC {10,15,20}  TF {8,12,16}  MATCHING {12,18,24}
--      ODD {10,15,20} ERROR {12,18,24}
--
-- Fix UUID-k: a seed determinisztikus és idempotens (egyszer fut, Flyway V7).
-- =========================================================================

-- =========================================================================
-- 1) ANGOL B1-B2 BŐVÍTÉS  (language_id = ...e0, order_index 5..9)
-- =========================================================================

INSERT INTO lessons (id, language_id, order_index, title, theory_markdown, enabled) VALUES
    ('00000000-0000-0000-0000-00000000050a',
     '00000000-0000-0000-0000-0000000000e0', 5, 'Phrasal verbs: vonzatos igék a mindennapokban',
     E'# Phrasal verbs\n\nA **phrasal verb** egy ige és egy (vagy két) partikula (prepozíció vagy határozószó) kombinációja, amelynek jelentése gyakran **eltér** az alkotóelemek szó szerinti jelentésétől. Ezek a hétköznapi angol beszéd alapkövei.\n\n'
     || E'## Gyakori példák\n\n- *give up* = feladni (nem "lefelé adni"): *Don''t give up!*\n- *look after* = vigyázni valakire: *She looks after her sister.*\n- *run into* = véletlenül összefutni valakivel: *I ran into an old friend.*\n- *put off* = elhalasztani: *They put off the meeting.*\n\n'
     || E'## Elválasztható vagy sem?\n\nNéhány phrasal verb **elválasztható**: a tárgy a partikula elé is kerülhet (*turn the light off* / *turn off the light*). Ha a tárgy névmás, akkor KÖTELEZŐEN közé kerül: *turn it off* (NEM *turn off it*).\n\n'
     || E'A jelentést legtöbbször a szövegkörnyezetből és a gyakorlásból lehet megtanulni — érdemes egész kifejezésként megjegyezni őket.',
     TRUE),
    ('00000000-0000-0000-0000-00000000060a',
     '00000000-0000-0000-0000-0000000000e0', 6, 'Idiómák: amit nem szabad szó szerint érteni',
     E'# Idiómák\n\nAz **idióma** olyan állandósult szókapcsolat, amelynek jelentése nem következik az egyes szavakból. Ezek teszik természetessé és árnyalttá a nyelvhasználatot.\n\n'
     || E'## Néhány klasszikus\n\n- *piece of cake* = gyerekjáték, nagyon könnyű dolog\n- *break the ice* = feloldani a kezdeti feszültséget\n- *hit the books* = nekiülni tanulni\n- *under the weather* = gyengélkedni, rosszul érezni magát\n- *cost an arm and a leg* = nagyon drága\n\n'
     || E'## Miért fontosak?\n\nAz idiómák kultúrafüggők, ezért a szó szerinti fordítás félrevezető. A *it''s raining cats and dogs* nem macskákról és kutyákról szól, hanem azt jelenti, hogy **zuhog az eső**.\n\n'
     || E'Tipp: egy idiómát mindig egész egységként tanulj meg, és figyeld, milyen helyzetben használják anyanyelvi beszélők.',
     TRUE),
    ('00000000-0000-0000-0000-00000000070a',
     '00000000-0000-0000-0000-0000000000e0', 7, 'Present perfect vs. past simple',
     E'# Present perfect vs. past simple\n\nA két múlt idő közötti választás a magyar anyanyelvűeknek gyakran nehéz, mert a magyarban egyetlen múlt idő van.\n\n'
     || E'## Past simple\n\n**Befejezett, konkrét időponthoz köthető** múltbeli esemény. Gyakran áll mellette időhatározó (*yesterday*, *in 2010*, *last week*).\n\n- *I visited London in 2019.*\n- *She called you an hour ago.*\n\n'
     || E'## Present perfect\n\nA múlt **kapcsolatban van a jelennel**: eredmény, tapasztalat, vagy be nem fejezett időszak. Jellemző szavak: *ever*, *never*, *already*, *yet*, *just*, *since*, *for*.\n\n- *I have visited London three times.* (tapasztalat, nem számít mikor)\n- *She has just called.* (az imént, hatása a jelenre)\n\n'
     || E'## A kulcskülönbség\n\nHa a konkrét **időpont** számít -> past simple. Ha az **eredmény vagy a tapasztalat** számít, és az idő nyitott -> present perfect. Figyeld: *I lost my keys* (egyszer, a múltban) vs. *I have lost my keys* (és most sincsenek meg).',
     TRUE),
    ('00000000-0000-0000-0000-00000000080a',
     '00000000-0000-0000-0000-0000000000e0', 8, 'Feltételes mondatok (conditionals)',
     E'# Feltételes mondatok\n\nA feltételes mondatok egy feltételt (*if*-tagmondat) és egy következményt (főmondat) kötnek össze. Négy fő típust különböztetünk meg.\n\n'
     || E'## Zero conditional\n\nÁltalános igazság, természeti törvény: **if + present, present**.\n\n- *If you heat ice, it melts.*\n\n'
     || E'## First conditional\n\nValós, jövőbeli lehetőség: **if + present, will + ige**.\n\n- *If it rains, we will stay home.*\n\n'
     || E'## Second conditional\n\nValószínűtlen vagy képzeletbeli jelen: **if + past, would + ige**.\n\n- *If I had more time, I would learn Spanish.*\n\n'
     || E'## Third conditional\n\nA múlt meg nem valósult feltétele: **if + past perfect, would have + 3. alak**.\n\n- *If I had studied, I would have passed.*\n\n'
     || E'Figyeld: a *would* SOHA nem áll az *if*-tagmondatban — ez gyakori hiba.',
     TRUE),
    ('00000000-0000-0000-0000-00000000090a',
     '00000000-0000-0000-0000-0000000000e0', 9, 'Vonzatos igék: dependent prepositions',
     E'# Dependent prepositions\n\nSok angol igének, melléknévnek és főnévnek **kötött elöljárószava** van, amelyet együtt kell megtanulni a szóval. A magyar vonzat gyakran MÁS, ezért nem lehet tükörfordítani.\n\n'
     || E'## Gyakori ige + prepozíció\n\n- *depend **on*** = függ valamitől\n- *listen **to*** = hallgat valamit/valakit\n- *wait **for*** = várni valakire/valamire\n- *believe **in*** = hinni valamiben\n- *apologize **for*** = bocsánatot kérni valamiért\n\n'
     || E'## Melléknév + prepozíció\n\n- *good **at*** = jó valamiben\n- *interested **in*** = érdeklődik valami iránt\n- *afraid **of*** = fél valamitől\n\n'
     || E'## Tipikus hibák\n\nMagyar hatásra sokan *listen the music* vagy *depend from* alakot írnak — helyesen *listen **to** the music* és *depend **on***. Ezeket érdemes egész kifejezésként rögzíteni.',
     TRUE);

-- --------------------------------------------- ANGOL LECKE 5 (phrasal verbs, MEDIUM) --
INSERT INTO exercises
    (id, lesson_id, type, difficulty, order_index, prompt, payload, solution,
     max_points, required_for_completion, duel_eligible, enabled) VALUES
    ('00000000-0000-0000-0000-000000015001',
     '00000000-0000-0000-0000-00000000050a', 'MULTIPLE_CHOICE', 'MEDIUM', 1,
     'Mit jelent a "give up" phrasal verb?',
     '{"multiSelect": false, "options": [
        {"id": "a", "text": "feladni"},
        {"id": "b", "text": "felemelni"},
        {"id": "c", "text": "visszaadni"}
      ]}'::jsonb,
     '{"correctOptionIds": ["a"]}'::jsonb,
     15, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-000000015002',
     '00000000-0000-0000-0000-00000000050a', 'TRUE_FALSE', 'MEDIUM', 2,
     'Igaz-e az állítás a névmásos tárgyról?',
     '{"statement": "Névmási tárgynál helyes: \"turn it off\" (nem \"turn off it\")."}'::jsonb,
     '{"answer": true}'::jsonb,
     12, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-000000015003',
     '00000000-0000-0000-0000-00000000050a', 'MATCHING', 'MEDIUM', 3,
     'Párosítsd a phrasal verböket a magyar jelentésükkel!',
     '{"leftItems": [{"id": "l1", "text": "look after"}, {"id": "l2", "text": "put off"}, {"id": "l3", "text": "run into"}],
       "rightItems": [{"id": "r1", "text": "vigyázni valakire"}, {"id": "r2", "text": "elhalasztani"}, {"id": "r3", "text": "véletlenül összefutni valakivel"}]}'::jsonb,
     '{"pairs": [{"leftId": "l1", "rightId": "r1"}, {"leftId": "l2", "rightId": "r2"}, {"leftId": "l3", "rightId": "r3"}]}'::jsonb,
     18, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-000000015004',
     '00000000-0000-0000-0000-00000000050a', 'ODD_ONE_OUT', 'MEDIUM', 4,
     'Melyik NEM phrasal verb (hanem egyszerű ige)?',
     '{"items": [{"id": "i1", "text": "give up"}, {"id": "i2", "text": "look after"}, {"id": "i3", "text": "understand"}, {"id": "i4", "text": "put off"}]}'::jsonb,
     '{"oddItemId": "i3"}'::jsonb,
     15, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-000000015005',
     '00000000-0000-0000-0000-00000000050a', 'ERROR_FINDING', 'MEDIUM', 5,
     'Jelöld meg a hibás szót! (helyesen: "Please turn it off.")',
     '{"tokens": [{"index": 0, "text": "Please"}, {"index": 1, "text": "turn"}, {"index": 2, "text": "off"}, {"index": 3, "text": "it"}]}'::jsonb,
     '{"incorrectTokenIndexes": [2, 3]}'::jsonb,
     18, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-000000015006',
     '00000000-0000-0000-0000-00000000050a', 'TRANSLATION', 'MEDIUM', 6,
     'Fordítsd le angolra (használj phrasal verbet): "Ne add fel!"',
     '{"sourceText": "Ne add fel!", "targetLanguageCode": "en",
       "rubric": ["phrasal verb helyes használata", "felszólító mód", "nyelvtani helyesség"]}'::jsonb,
     NULL,
     15, FALSE, FALSE, TRUE),
    ('00000000-0000-0000-0000-000000015007',
     '00000000-0000-0000-0000-00000000050a', 'AI_SCENARIO', 'MEDIUM', 7,
     'Beszéld meg egy baráttal a terveid elhalasztását angolul!',
     '{"role": "egy barát", "situation": "Elhalasztottatok egy közös programot, ezt beszélitek meg.",
       "goals": ["magyarázd el, miért kell elhalasztani", "használj legalább egy phrasal verböt", "javasolj új időpontot"],
       "targetLanguageCode": "en", "openingMessage": "Hey, so why do we need to put off our plans?"}'::jsonb,
     NULL,
     30, FALSE, FALSE, TRUE);

-- --------------------------------------------- ANGOL LECKE 6 (idiómák, MEDIUM/HARD) --
INSERT INTO exercises
    (id, lesson_id, type, difficulty, order_index, prompt, payload, solution,
     max_points, required_for_completion, duel_eligible, enabled) VALUES
    ('00000000-0000-0000-0000-000000016001',
     '00000000-0000-0000-0000-00000000060a', 'MULTIPLE_CHOICE', 'MEDIUM', 1,
     'Mit jelent a "piece of cake" idióma?',
     '{"multiSelect": false, "options": [
        {"id": "a", "text": "egy szelet torta"},
        {"id": "b", "text": "nagyon könnyű dolog"},
        {"id": "c", "text": "drága dolog"}
      ]}'::jsonb,
     '{"correctOptionIds": ["b"]}'::jsonb,
     15, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-000000016002',
     '00000000-0000-0000-0000-00000000060a', 'TRUE_FALSE', 'MEDIUM', 2,
     'Igaz-e az állítás az idiómáról?',
     '{"statement": "A \"cost an arm and a leg\" azt jelenti, hogy valami nagyon drága."}'::jsonb,
     '{"answer": true}'::jsonb,
     12, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-000000016003',
     '00000000-0000-0000-0000-00000000060a', 'MATCHING', 'HARD', 3,
     'Párosítsd az idiómákat a jelentésükkel!',
     '{"leftItems": [{"id": "l1", "text": "break the ice"}, {"id": "l2", "text": "under the weather"}, {"id": "l3", "text": "hit the books"}],
       "rightItems": [{"id": "r1", "text": "feloldani a feszültséget"}, {"id": "r2", "text": "gyengélkedni"}, {"id": "r3", "text": "nekiülni tanulni"}]}'::jsonb,
     '{"pairs": [{"leftId": "l1", "rightId": "r1"}, {"leftId": "l2", "rightId": "r2"}, {"leftId": "l3", "rightId": "r3"}]}'::jsonb,
     24, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-000000016004',
     '00000000-0000-0000-0000-00000000060a', 'ODD_ONE_OUT', 'MEDIUM', 4,
     'Melyik kifejezés NEM jelent "könnyű dolgot"?',
     '{"items": [{"id": "i1", "text": "piece of cake"}, {"id": "i2", "text": "a breeze"}, {"id": "i3", "text": "cost an arm and a leg"}, {"id": "i4", "text": "child''s play"}]}'::jsonb,
     '{"oddItemId": "i3"}'::jsonb,
     15, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-000000016005',
     '00000000-0000-0000-0000-00000000060a', 'ERROR_FINDING', 'HARD', 5,
     'Jelöld meg a hibás szót! (helyes idióma: "break the ice")',
     '{"tokens": [{"index": 0, "text": "Let''s"}, {"index": 1, "text": "break"}, {"index": 2, "text": "a"}, {"index": 3, "text": "ice"}]}'::jsonb,
     '{"incorrectTokenIndexes": [2]}'::jsonb,
     24, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-000000016006',
     '00000000-0000-0000-0000-00000000060a', 'TRANSLATION', 'HARD', 6,
     'Fordítsd le angolra idiómával: "Ez gyerekjáték volt."',
     '{"sourceText": "Ez gyerekjáték volt.", "targetLanguageCode": "en",
       "rubric": ["idióma helyes használata", "múlt idő", "nyelvtani helyesség"]}'::jsonb,
     NULL,
     15, FALSE, FALSE, TRUE),
    ('00000000-0000-0000-0000-000000016007',
     '00000000-0000-0000-0000-00000000060a', 'AI_SCENARIO', 'HARD', 7,
     'Kezdj el egy beszélgetést egy rendezvényen angolul!',
     '{"role": "egy ismeretlen vendég", "situation": "Egy rendezvényen állsz egy ismeretlen mellett, beszélgetést kezdeményezel.",
       "goals": ["törd meg a jeget egy kérdéssel", "használj legalább egy idiómát", "tarts fenn egy rövid beszélgetést"],
       "targetLanguageCode": "en", "openingMessage": "Hi there! Great party, isn''t it?"}'::jsonb,
     NULL,
     30, FALSE, FALSE, TRUE);

-- --------------------------------------------- ANGOL LECKE 7 (present perfect vs past simple, HARD) --
INSERT INTO exercises
    (id, lesson_id, type, difficulty, order_index, prompt, payload, solution,
     max_points, required_for_completion, duel_eligible, enabled) VALUES
    ('00000000-0000-0000-0000-000000017001',
     '00000000-0000-0000-0000-00000000070a', 'MULTIPLE_CHOICE', 'HARD', 1,
     'Melyik a helyes: "I ___ London in 2019."?',
     '{"multiSelect": false, "options": [
        {"id": "a", "text": "have visited"},
        {"id": "b", "text": "visited"},
        {"id": "c", "text": "has visited"}
      ]}'::jsonb,
     '{"correctOptionIds": ["b"]}'::jsonb,
     20, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-000000017002',
     '00000000-0000-0000-0000-00000000070a', 'TRUE_FALSE', 'HARD', 2,
     'Igaz-e az állítás az időhatározókról?',
     '{"statement": "A \"yesterday\" időhatározó past simple-lel jár, nem present perfecttel."}'::jsonb,
     '{"answer": true}'::jsonb,
     16, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-000000017003',
     '00000000-0000-0000-0000-00000000070a', 'MATCHING', 'HARD', 3,
     'Párosítsd az időhatározót a hozzá illő igeidővel!',
     '{"leftItems": [{"id": "l1", "text": "yesterday"}, {"id": "l2", "text": "just"}, {"id": "l3", "text": "in 2010"}],
       "rightItems": [{"id": "r1", "text": "past simple"}, {"id": "r2", "text": "present perfect"}, {"id": "r3", "text": "past simple"}]}'::jsonb,
     '{"pairs": [{"leftId": "l1", "rightId": "r1"}, {"leftId": "l2", "rightId": "r2"}, {"leftId": "l3", "rightId": "r3"}]}'::jsonb,
     24, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-000000017004',
     '00000000-0000-0000-0000-00000000070a', 'ODD_ONE_OUT', 'MEDIUM', 4,
     'Melyik időhatározó NEM present perfecthez illik?',
     '{"items": [{"id": "i1", "text": "ever"}, {"id": "i2", "text": "already"}, {"id": "i3", "text": "two days ago"}, {"id": "i4", "text": "since"}]}'::jsonb,
     '{"oddItemId": "i3"}'::jsonb,
     15, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-000000017005',
     '00000000-0000-0000-0000-00000000070a', 'ERROR_FINDING', 'HARD', 5,
     'Jelöld meg a hibás szót! (helyesen: "I visited her yesterday.")',
     '{"tokens": [{"index": 0, "text": "I"}, {"index": 1, "text": "have"}, {"index": 2, "text": "visited"}, {"index": 3, "text": "her"}, {"index": 4, "text": "yesterday"}]}'::jsonb,
     '{"incorrectTokenIndexes": [1]}'::jsonb,
     24, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-000000017006',
     '00000000-0000-0000-0000-00000000070a', 'TRANSLATION', 'HARD', 6,
     'Fordítsd le angolra (present perfect): "Már háromszor jártam Londonban."',
     '{"sourceText": "Már háromszor jártam Londonban.", "targetLanguageCode": "en",
       "rubric": ["present perfect helyes használata", "tapasztalat kifejezése", "nyelvtani helyesség"]}'::jsonb,
     NULL,
     15, FALSE, FALSE, TRUE),
    ('00000000-0000-0000-0000-000000017007',
     '00000000-0000-0000-0000-00000000070a', 'AI_SCENARIO', 'HARD', 7,
     'Mesélj az utazási tapasztalataidról angolul!',
     '{"role": "egy útitárs", "situation": "Egy új ismerősöddel az utazási élményeitekről beszélgettek.",
       "goals": ["mondj el egy konkrét múltbeli utazást past simple-ben", "mondj egy általános tapasztalatot present perfectben", "kérdezd meg a másik élményeit"],
       "targetLanguageCode": "en", "openingMessage": "So, have you traveled much recently?"}'::jsonb,
     NULL,
     30, FALSE, FALSE, TRUE);

-- --------------------------------------------- ANGOL LECKE 8 (conditionals, HARD) --
INSERT INTO exercises
    (id, lesson_id, type, difficulty, order_index, prompt, payload, solution,
     max_points, required_for_completion, duel_eligible, enabled) VALUES
    ('00000000-0000-0000-0000-000000018001',
     '00000000-0000-0000-0000-00000000080a', 'MULTIPLE_CHOICE', 'HARD', 1,
     'Melyik a helyes first conditional: "If it ___, we will stay home."?',
     '{"multiSelect": false, "options": [
        {"id": "a", "text": "rains"},
        {"id": "b", "text": "will rain"},
        {"id": "c", "text": "rained"}
      ]}'::jsonb,
     '{"correctOptionIds": ["a"]}'::jsonb,
     20, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-000000018002',
     '00000000-0000-0000-0000-00000000080a', 'TRUE_FALSE', 'HARD', 2,
     'Igaz-e az állítás a feltételes mondatokról?',
     '{"statement": "A \"would\" soha nem állhat az if-tagmondatban."}'::jsonb,
     '{"answer": true}'::jsonb,
     16, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-000000018003',
     '00000000-0000-0000-0000-00000000080a', 'MATCHING', 'HARD', 3,
     'Párosítsd a conditional típust a példával!',
     '{"leftItems": [{"id": "l1", "text": "zero conditional"}, {"id": "l2", "text": "first conditional"}, {"id": "l3", "text": "second conditional"}],
       "rightItems": [{"id": "r1", "text": "If you heat ice, it melts."}, {"id": "r2", "text": "If it rains, we will stay home."}, {"id": "r3", "text": "If I had time, I would travel."}]}'::jsonb,
     '{"pairs": [{"leftId": "l1", "rightId": "r1"}, {"leftId": "l2", "rightId": "r2"}, {"leftId": "l3", "rightId": "r3"}]}'::jsonb,
     24, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-000000018004',
     '00000000-0000-0000-0000-00000000080a', 'ODD_ONE_OUT', 'MEDIUM', 4,
     'Melyik tagmondat NEM helyes feltételes szerkezet?',
     '{"items": [{"id": "i1", "text": "If I were you"}, {"id": "i2", "text": "If she comes"}, {"id": "i3", "text": "If I would know"}, {"id": "i4", "text": "If it rains"}]}'::jsonb,
     '{"oddItemId": "i3"}'::jsonb,
     15, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-000000018005',
     '00000000-0000-0000-0000-00000000080a', 'ERROR_FINDING', 'HARD', 5,
     'Jelöld meg a hibás szót! (helyesen: "If I had time, I would travel.")',
     '{"tokens": [{"index": 0, "text": "If"}, {"index": 1, "text": "I"}, {"index": 2, "text": "would"}, {"index": 3, "text": "have"}, {"index": 4, "text": "time"}]}'::jsonb,
     '{"incorrectTokenIndexes": [2]}'::jsonb,
     24, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-000000018006',
     '00000000-0000-0000-0000-00000000080a', 'TRANSLATION', 'HARD', 6,
     'Fordítsd le angolra (second conditional): "Ha több időm lenne, spanyolul tanulnék."',
     '{"sourceText": "Ha több időm lenne, spanyolul tanulnék.", "targetLanguageCode": "en",
       "rubric": ["second conditional szerkezet", "would + ige", "nyelvtani helyesség"]}'::jsonb,
     NULL,
     15, FALSE, FALSE, TRUE),
    ('00000000-0000-0000-0000-000000018007',
     '00000000-0000-0000-0000-00000000080a', 'AI_SCENARIO', 'HARD', 7,
     'Beszélj a jövőbeli terveidről feltételekkel angolul!',
     '{"role": "egy pályaválasztási tanácsadó", "situation": "A tanácsadóval a lehetséges jövődről beszélgettek.",
       "goals": ["fogalmazz meg egy valós jövőbeli tervet first conditionallel", "fogalmazz meg egy képzeletbeli helyzetet second conditionallel", "kérj tanácsot"],
       "targetLanguageCode": "en", "openingMessage": "Tell me, what will you do if you get the job?"}'::jsonb,
     NULL,
     30, FALSE, FALSE, TRUE);

-- --------------------------------------------- ANGOL LECKE 9 (dependent prepositions, HARD) --
INSERT INTO exercises
    (id, lesson_id, type, difficulty, order_index, prompt, payload, solution,
     max_points, required_for_completion, duel_eligible, enabled) VALUES
    ('00000000-0000-0000-0000-000000019001',
     '00000000-0000-0000-0000-00000000090a', 'MULTIPLE_CHOICE', 'HARD', 1,
     'Melyik a helyes: "It depends ___ the weather."?',
     '{"multiSelect": false, "options": [
        {"id": "a", "text": "from"},
        {"id": "b", "text": "on"},
        {"id": "c", "text": "of"}
      ]}'::jsonb,
     '{"correctOptionIds": ["b"]}'::jsonb,
     20, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-000000019002',
     '00000000-0000-0000-0000-00000000090a', 'TRUE_FALSE', 'MEDIUM', 2,
     'Igaz-e az állítás a vonzatokról?',
     '{"statement": "Helyesen: \"listen to the music\" (nem \"listen the music\")."}'::jsonb,
     '{"answer": true}'::jsonb,
     12, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-000000019003',
     '00000000-0000-0000-0000-00000000090a', 'MATCHING', 'HARD', 3,
     'Párosítsd az igét/melléknevet a helyes elöljárószóval!',
     '{"leftItems": [{"id": "l1", "text": "wait ___"}, {"id": "l2", "text": "good ___"}, {"id": "l3", "text": "afraid ___"}],
       "rightItems": [{"id": "r1", "text": "for"}, {"id": "r2", "text": "at"}, {"id": "r3", "text": "of"}]}'::jsonb,
     '{"pairs": [{"leftId": "l1", "rightId": "r1"}, {"leftId": "l2", "rightId": "r2"}, {"leftId": "l3", "rightId": "r3"}]}'::jsonb,
     24, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-000000019004',
     '00000000-0000-0000-0000-00000000090a', 'ODD_ONE_OUT', 'MEDIUM', 4,
     'Melyik vonzat HIBÁS?',
     '{"items": [{"id": "i1", "text": "believe in"}, {"id": "i2", "text": "listen to"}, {"id": "i3", "text": "depend from"}, {"id": "i4", "text": "apologize for"}]}'::jsonb,
     '{"oddItemId": "i3"}'::jsonb,
     15, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-000000019005',
     '00000000-0000-0000-0000-00000000090a', 'ERROR_FINDING', 'HARD', 5,
     'Jelöld meg a hibás szót! (helyesen: "I am interested in art.")',
     '{"tokens": [{"index": 0, "text": "I"}, {"index": 1, "text": "am"}, {"index": 2, "text": "interested"}, {"index": 3, "text": "for"}, {"index": 4, "text": "art"}]}'::jsonb,
     '{"incorrectTokenIndexes": [3]}'::jsonb,
     24, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-000000019006',
     '00000000-0000-0000-0000-00000000090a', 'TRANSLATION', 'HARD', 6,
     'Fordítsd le angolra (helyes vonzattal): "Az időjárástól függ."',
     '{"sourceText": "Az időjárástól függ.", "targetLanguageCode": "en",
       "rubric": ["helyes vonzat (depend on)", "nyelvtani helyesség", "szókincs"]}'::jsonb,
     NULL,
     15, FALSE, FALSE, TRUE),
    ('00000000-0000-0000-0000-000000019007',
     '00000000-0000-0000-0000-00000000090a', 'AI_SCENARIO', 'HARD', 7,
     'Beszélj az érdeklődési köreidről angolul!',
     '{"role": "egy új kolléga", "situation": "Egy új kollégáddal a hobbijaitokról és érdeklődésetekről beszélgettek.",
       "goals": ["mondd el, mi érdekel (interested in)", "mondd el, miben vagy jó (good at)", "kérdezd meg a másik érdeklődését"],
       "targetLanguageCode": "en", "openingMessage": "So, what are you interested in outside of work?"}'::jsonb,
     NULL,
     30, FALSE, FALSE, TRUE);

-- =========================================================================
-- 2) NÉMET ÚJ NYELV  (fix UUID) + 3 lecke (order_index 1..3), kezdő->A2
-- =========================================================================

INSERT INTO languages (id, code, name, enabled) VALUES
    ('00000000-0000-0000-0000-0000000000de', 'de', 'Német', TRUE);

INSERT INTO lessons (id, language_id, order_index, title, theory_markdown, enabled) VALUES
    ('00000000-0000-0000-0000-0000000001de',
     '00000000-0000-0000-0000-0000000000de', 1, 'Üdvözlés és alapszavak',
     E'# Üdvözlés és alapszavak\n\nA német tanulás első lépése néhány gyakori **üdvözlés** és **főnév** megismerése.\n\n'
     || E'## Üdvözlések\n\n- *Hallo* = Szia\n- *Guten Morgen* = Jó reggelt\n- *Danke* = Köszönöm\n- *Tschüss* = Szia (búcsúzáskor)\n\n'
     || E'## Főnevek és névelők\n\nA német főnevek **neme** háromféle lehet, és nagy kezdőbetűvel írjuk őket:\n\n- *der Hund* = a kutya (hímnem)\n- *die Katze* = a macska (nőnem)\n- *das Haus* = a ház (semleges)\n\nA névelő megtanulása a főnévvel együtt kulcsfontosságú.',
     TRUE),
    ('00000000-0000-0000-0000-0000000002de',
     '00000000-0000-0000-0000-0000000000de', 2, 'Számok és színek',
     E'# Számok és színek\n\nGyakori **számok** és **színek** németül.\n\n'
     || E'## Számok\n\n- *eins* = egy, *zwei* = kettő, *drei* = három\n- *vier* = négy, *fünf* = öt\n\n'
     || E'## Színek\n\n- *rot* = piros, *blau* = kék, *grün* = zöld, *gelb* = sárga\n\n'
     || E'A színek melléknévként a főnév előtt állnak, és a mondat szerkezetétől függően ragozódhatnak: *ein rotes Auto* (egy piros autó).',
     TRUE),
    ('00000000-0000-0000-0000-0000000003de',
     '00000000-0000-0000-0000-0000000000de', 3, 'Igék és egyszerű mondatok',
     E'# Igék és egyszerű mondatok\n\nA német igék a jelen időben személyre ragozódnak. Nézzük a *sein* (lenni) és a *haben* (birtokolni) igét.\n\n'
     || E'## sein (lenni)\n\n- *ich bin* = én vagyok\n- *du bist* = te vagy\n- *er/sie ist* = ő van\n\n'
     || E'## haben (birtokolni)\n\n- *ich habe* = nekem van\n- *du hast* = neked van\n\n'
     || E'## Szórend\n\nKijelentő mondatban az **ige a második helyen** áll: *Ich lerne Deutsch.* (Németül tanulok.) Ez a német mondatszerkezet egyik alapszabálya.',
     TRUE);

-- --------------------------------------------- NÉMET LECKE 1 (EASY) --
INSERT INTO exercises
    (id, lesson_id, type, difficulty, order_index, prompt, payload, solution,
     max_points, required_for_completion, duel_eligible, enabled) VALUES
    ('00000000-0000-0000-0000-00000001de01',
     '00000000-0000-0000-0000-0000000001de', 'MULTIPLE_CHOICE', 'EASY', 1,
     'Mit jelent németül a "Danke"?',
     '{"multiSelect": false, "options": [
        {"id": "a", "text": "Szia"},
        {"id": "b", "text": "Köszönöm"},
        {"id": "c", "text": "Jó reggelt"}
      ]}'::jsonb,
     '{"correctOptionIds": ["b"]}'::jsonb,
     10, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-00000001de02',
     '00000000-0000-0000-0000-0000000001de', 'TRUE_FALSE', 'EASY', 2,
     'Igaz-e az állítás a német főnevekről?',
     '{"statement": "A német főneveket nagy kezdőbetűvel írjuk."}'::jsonb,
     '{"answer": true}'::jsonb,
     8, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-00000001de03',
     '00000000-0000-0000-0000-0000000001de', 'MATCHING', 'EASY', 3,
     'Párosítsd a német szavakat a magyar jelentésükkel!',
     '{"leftItems": [{"id": "l1", "text": "Hund"}, {"id": "l2", "text": "Katze"}, {"id": "l3", "text": "Haus"}],
       "rightItems": [{"id": "r1", "text": "kutya"}, {"id": "r2", "text": "macska"}, {"id": "r3", "text": "ház"}]}'::jsonb,
     '{"pairs": [{"leftId": "l1", "rightId": "r1"}, {"leftId": "l2", "rightId": "r2"}, {"leftId": "l3", "rightId": "r3"}]}'::jsonb,
     12, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-00000001de04',
     '00000000-0000-0000-0000-0000000001de', 'ODD_ONE_OUT', 'EASY', 4,
     'Melyik szó lóg ki a sorból (nem üdvözlés)?',
     '{"items": [{"id": "i1", "text": "Hallo"}, {"id": "i2", "text": "Tschüss"}, {"id": "i3", "text": "Haus"}]}'::jsonb,
     '{"oddItemId": "i3"}'::jsonb,
     10, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-00000001de05',
     '00000000-0000-0000-0000-0000000001de', 'ERROR_FINDING', 'MEDIUM', 5,
     'Jelöld meg a hibás szót! (a macska németül "die Katze", nem "der")',
     '{"tokens": [{"index": 0, "text": "der"}, {"index": 1, "text": "Katze"}]}'::jsonb,
     '{"incorrectTokenIndexes": [0]}'::jsonb,
     18, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-00000001de06',
     '00000000-0000-0000-0000-0000000001de', 'TRANSLATION', 'EASY', 6,
     'Fordítsd le németre: "Jó reggelt!"',
     '{"sourceText": "Jó reggelt!", "targetLanguageCode": "de",
       "rubric": ["helyes üdvözlés", "helyesírás", "nagy kezdőbetű"]}'::jsonb,
     NULL,
     15, FALSE, FALSE, TRUE),
    ('00000000-0000-0000-0000-00000001de07',
     '00000000-0000-0000-0000-0000000001de', 'AI_SCENARIO', 'EASY', 7,
     'Köszönj és mutatkozz be németül!',
     '{"role": "egy új ismerős", "situation": "Egy nyelvi klubban találkoztok, bemutatkozol németül.",
       "goals": ["köszönj (Hallo)", "mondd meg a neved", "búcsúzz el (Tschüss)"],
       "targetLanguageCode": "de", "openingMessage": "Hallo! Wie heißt du?"}'::jsonb,
     NULL,
     30, FALSE, FALSE, TRUE);

-- --------------------------------------------- NÉMET LECKE 2 (EASY/MEDIUM) --
INSERT INTO exercises
    (id, lesson_id, type, difficulty, order_index, prompt, payload, solution,
     max_points, required_for_completion, duel_eligible, enabled) VALUES
    ('00000000-0000-0000-0000-00000002de01',
     '00000000-0000-0000-0000-0000000002de', 'MULTIPLE_CHOICE', 'MEDIUM', 1,
     'Mit jelent németül a "blau"?',
     '{"multiSelect": false, "options": [
        {"id": "a", "text": "piros"},
        {"id": "b", "text": "kék"},
        {"id": "c", "text": "zöld"}
      ]}'::jsonb,
     '{"correctOptionIds": ["b"]}'::jsonb,
     15, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-00000002de02',
     '00000000-0000-0000-0000-0000000002de', 'TRUE_FALSE', 'EASY', 2,
     'Igaz-e az állítás?',
     '{"statement": "A \"drei\" szó jelentése öt."}'::jsonb,
     '{"answer": false}'::jsonb,
     8, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-00000002de03',
     '00000000-0000-0000-0000-0000000002de', 'MATCHING', 'MEDIUM', 3,
     'Párosítsd a német számokat a magyar jelentésükkel!',
     '{"leftItems": [{"id": "l1", "text": "eins"}, {"id": "l2", "text": "zwei"}, {"id": "l3", "text": "drei"}],
       "rightItems": [{"id": "r1", "text": "egy"}, {"id": "r2", "text": "kettő"}, {"id": "r3", "text": "három"}]}'::jsonb,
     '{"pairs": [{"leftId": "l1", "rightId": "r1"}, {"leftId": "l2", "rightId": "r2"}, {"leftId": "l3", "rightId": "r3"}]}'::jsonb,
     18, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-00000002de04',
     '00000000-0000-0000-0000-0000000002de', 'ODD_ONE_OUT', 'MEDIUM', 4,
     'Melyik szó lóg ki a sorból (nem szín)?',
     '{"items": [{"id": "i1", "text": "rot"}, {"id": "i2", "text": "grün"}, {"id": "i3", "text": "zwei"}, {"id": "i4", "text": "gelb"}]}'::jsonb,
     '{"oddItemId": "i3"}'::jsonb,
     15, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-00000002de05',
     '00000000-0000-0000-0000-0000000002de', 'ERROR_FINDING', 'MEDIUM', 5,
     'Jelöld meg a hibás szót! (helyesen: "ein rotes Auto")',
     '{"tokens": [{"index": 0, "text": "ein"}, {"index": 1, "text": "rot"}, {"index": 2, "text": "Auto"}]}'::jsonb,
     '{"incorrectTokenIndexes": [1]}'::jsonb,
     18, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-00000002de06',
     '00000000-0000-0000-0000-0000000002de', 'TRANSLATION', 'MEDIUM', 6,
     'Fordítsd le németre: "Három piros autó."',
     '{"sourceText": "Három piros autó.", "targetLanguageCode": "de",
       "rubric": ["számnév helyesen", "szín melléknév", "helyesírás"]}'::jsonb,
     NULL,
     15, FALSE, FALSE, TRUE),
    ('00000000-0000-0000-0000-00000002de07',
     '00000000-0000-0000-0000-0000000002de', 'AI_SCENARIO', 'MEDIUM', 7,
     'Vásárolj be egy üzletben németül!',
     '{"role": "egy eladó", "situation": "Egy üzletben vásárolsz, az eladóval beszélsz németül.",
       "goals": ["köszönj", "kérdezd meg egy tárgy színét", "köszönd meg a segítséget"],
       "targetLanguageCode": "de", "openingMessage": "Guten Tag! Wie kann ich Ihnen helfen?"}'::jsonb,
     NULL,
     30, FALSE, FALSE, TRUE);

-- --------------------------------------------- NÉMET LECKE 3 (MEDIUM/HARD) --
INSERT INTO exercises
    (id, lesson_id, type, difficulty, order_index, prompt, payload, solution,
     max_points, required_for_completion, duel_eligible, enabled) VALUES
    ('00000000-0000-0000-0000-00000003de01',
     '00000000-0000-0000-0000-0000000003de', 'MULTIPLE_CHOICE', 'MEDIUM', 1,
     'Melyik a helyes alak: "Ich ___ Deutsch." (tanulok)?',
     '{"multiSelect": false, "options": [
        {"id": "a", "text": "lerne"},
        {"id": "b", "text": "lernst"},
        {"id": "c", "text": "lernt"}
      ]}'::jsonb,
     '{"correctOptionIds": ["a"]}'::jsonb,
     15, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-00000003de02',
     '00000000-0000-0000-0000-0000000003de', 'TRUE_FALSE', 'MEDIUM', 2,
     'Igaz-e az állítás a német szórendről?',
     '{"statement": "Kijelentő mondatban az ige a második helyen áll."}'::jsonb,
     '{"answer": true}'::jsonb,
     12, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-00000003de03',
     '00000000-0000-0000-0000-0000000003de', 'MATCHING', 'MEDIUM', 3,
     'Párosítsd a névmást a "sein" (lenni) ige alakjával!',
     '{"leftItems": [{"id": "l1", "text": "ich"}, {"id": "l2", "text": "du"}, {"id": "l3", "text": "er"}],
       "rightItems": [{"id": "r1", "text": "bin"}, {"id": "r2", "text": "bist"}, {"id": "r3", "text": "ist"}]}'::jsonb,
     '{"pairs": [{"leftId": "l1", "rightId": "r1"}, {"leftId": "l2", "rightId": "r2"}, {"leftId": "l3", "rightId": "r3"}]}'::jsonb,
     18, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-00000003de04',
     '00000000-0000-0000-0000-0000000003de', 'ODD_ONE_OUT', 'MEDIUM', 4,
     'Melyik szó lóg ki a sorból (nem a "sein" ige alakja)?',
     '{"items": [{"id": "i1", "text": "bin"}, {"id": "i2", "text": "bist"}, {"id": "i3", "text": "habe"}, {"id": "i4", "text": "ist"}]}'::jsonb,
     '{"oddItemId": "i3"}'::jsonb,
     15, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-00000003de05',
     '00000000-0000-0000-0000-0000000003de', 'ERROR_FINDING', 'HARD', 5,
     'Jelöld meg a hibás szót! (helyesen: "Ich bin müde.")',
     '{"tokens": [{"index": 0, "text": "Ich"}, {"index": 1, "text": "bist"}, {"index": 2, "text": "müde"}]}'::jsonb,
     '{"incorrectTokenIndexes": [1]}'::jsonb,
     24, TRUE, TRUE, TRUE),
    ('00000000-0000-0000-0000-00000003de06',
     '00000000-0000-0000-0000-0000000003de', 'TRANSLATION', 'MEDIUM', 6,
     'Fordítsd le németre: "Németül tanulok."',
     '{"sourceText": "Németül tanulok.", "targetLanguageCode": "de",
       "rubric": ["ige a második helyen", "helyes ragozás", "helyesírás"]}'::jsonb,
     NULL,
     15, FALSE, FALSE, TRUE),
    ('00000000-0000-0000-0000-00000003de07',
     '00000000-0000-0000-0000-0000000003de', 'AI_SCENARIO', 'MEDIUM', 7,
     'Mesélj magadról németül!',
     '{"role": "egy barát", "situation": "Egy új baráttal beszélgetsz magadról németül.",
       "goals": ["mondd meg, hogy vagy (Ich bin ...)", "mondj egy tevékenységet (Ich lerne ...)", "kérdezd meg a másikat"],
       "targetLanguageCode": "de", "openingMessage": "Hallo! Erzähl mir etwas über dich."}'::jsonb,
     NULL,
     30, FALSE, FALSE, TRUE);
