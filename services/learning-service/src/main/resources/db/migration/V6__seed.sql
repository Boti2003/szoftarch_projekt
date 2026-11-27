-- =========================================================================
-- U9 seed tananyag (TERV 15.). CSAK tananyag: 1 nyelv (angol), 4 sorrendi
-- lecke elmélettel, leckénként 5 automatikus + 2 AI feladat. NINCS
-- felhasználói adat (enrollment/progress/attempt/daily/duel) — azok
-- futásidőben, felhasználónként keletkeznek.
--
-- A tananyag magyar anyanyelvűeknek szól: az elmélet és a promptok MAGYARUL
-- magyaráznak, a gyakorolt tartalom ANGOL. A JSONB payload/solution a U2
-- recordok mezőneveihez illeszkedik, hogy az ExerciseContentCodec
-- deszerializálja és az U3 evaluator a solution szerinti helyes válaszra
-- correct=true / score_ratio=1.0 eredményt adjon.
--
-- max_points = floor(alappont x nehézségi szorzó) (TERV 5.1 / U3):
--   alappont MC 10 / TF 8 / MATCHING 12 / ODD_ONE_OUT 10 / ERROR_FINDING 12
--            TRANSLATION 15 / AI_SCENARIO 30 (AI, fix)
--   szorzó   EASY 1.0 / MEDIUM 1.5 / HARD 2.0
--
-- Fix UUID-k: a seed determinisztikus és hivatkozható. Egyszer fut (Flyway V6).
-- =========================================================================

-- ------------------------------------------------------------------ nyelv --
INSERT INTO languages (id, code, name, enabled) VALUES
    ('00000000-0000-0000-0000-0000000000e0', 'en', 'Angol', TRUE);

-- ----------------------------------------------------------------- leckék --
-- A nehézség leckénként haladjon (korai EASY/MEDIUM, későbbi MEDIUM/HARD).
INSERT INTO lessons (id, language_id, order_index, title, theory_markdown, enabled) VALUES
    ('00000000-0000-0000-0000-00000000010a',
     '00000000-0000-0000-0000-0000000000e0', 1, 'Alapszavak: állatok és tárgyak',
     E'# Alapszavak\n\nAz angol tanulás első lépése néhány gyakori **főnév** megismerése.\n\n'
     || E'- *cat* = macska\n- *dog* = kutya\n- *house* = ház\n- *book* = könyv\n\n'
     || E'A főnevek előtt gyakran áll az **a/an** határozatlan névelő: *a cat*, *an apple*.',
     TRUE),
    ('00000000-0000-0000-0000-00000000020a',
     '00000000-0000-0000-0000-0000000000e0', 2, 'Színek és számok',
     E'# Színek és számok\n\nGyakori **színek** és **számok** angolul.\n\n'
     || E'- *red* = piros, *blue* = kék, *green* = zöld\n- *one* = egy, *two* = kettő, *three* = három\n\n'
     || E'A színek a főnév előtt állnak: *a red car* (egy piros autó).',
     TRUE),
    ('00000000-0000-0000-0000-00000000030a',
     '00000000-0000-0000-0000-0000000000e0', 3, 'Igék és egyszerű jelen idő',
     E'# Igék jelen időben\n\nAz egyszerű jelen (*present simple*) harmadik személyben **-s** ragot kap.\n\n'
     || E'- *I go* / *she goes*\n- *I eat* / *he eats*\n\n'
     || E'Gyakori hiba a *she go* — helyesen *she goes*.',
     TRUE),
    ('00000000-0000-0000-0000-00000000040a',
     '00000000-0000-0000-0000-0000000000e0', 4, 'Mondatok és kérdések',
     E'# Mondatok és kérdések\n\nKérdést a *do/does* segédigével képzünk.\n\n'
     || E'- *Do you like coffee?* (Szereted a kávét?)\n- *Does she speak English?*\n\n'
     || E'Tagadás: *I do not (don''t) know.* A szórend fontos: alany + ige + tárgy.',
     TRUE);

-- ========================================================================= --
-- Feladatok. Oszloprend:
--   (id, lesson_id, type, difficulty, order_index, prompt, payload, solution,
--    max_points, required_for_completion, duel_eligible, enabled)
-- Az AUTO feladatok required=true + duel_eligible (lecke 1-3: duel_eligible=TRUE,
-- lecke 4: FALSE), az AI feladatok required=false + duel_eligible=FALSE.
-- ========================================================================= --

-- --------------------------------------------- LECKE 1 (EASY) auto ------- --
INSERT INTO exercises
    (id, lesson_id, type, difficulty, order_index, prompt, payload, solution,
     max_points, required_for_completion, duel_eligible, enabled) VALUES
    -- MULTIPLE_CHOICE (EASY, base 10 -> 10)
    ('00000000-0000-0000-0000-000000011001',
     '00000000-0000-0000-0000-00000000010a', 'MULTIPLE_CHOICE', 'EASY', 1,
     'Mit jelent angolul a "cat"?',
     '{"multiSelect": false, "options": [
        {"id": "a", "text": "kutya"},
        {"id": "b", "text": "macska"},
        {"id": "c", "text": "ház"}
      ]}'::jsonb,
     '{"correctOptionIds": ["b"]}'::jsonb,
     10, TRUE, TRUE, TRUE),
    -- TRUE_FALSE (EASY, base 8 -> 8)
    ('00000000-0000-0000-0000-000000011002',
     '00000000-0000-0000-0000-00000000010a', 'TRUE_FALSE', 'EASY', 2,
     'Igaz-e, hogy a "dog" jelentése kutya?',
     '{"statement": "A \"dog\" szó jelentése kutya."}'::jsonb,
     '{"answer": true}'::jsonb,
     8, TRUE, TRUE, TRUE),
    -- MATCHING (EASY, base 12 -> 12)
    ('00000000-0000-0000-0000-000000011003',
     '00000000-0000-0000-0000-00000000010a', 'MATCHING', 'EASY', 3,
     'Párosítsd az angol szavakat a magyar jelentésükkel!',
     '{"leftItems": [{"id": "l1", "text": "cat"}, {"id": "l2", "text": "dog"}, {"id": "l3", "text": "house"}],
       "rightItems": [{"id": "r1", "text": "macska"}, {"id": "r2", "text": "kutya"}, {"id": "r3", "text": "ház"}]}'::jsonb,
     '{"pairs": [{"leftId": "l1", "rightId": "r1"}, {"leftId": "l2", "rightId": "r2"}, {"leftId": "l3", "rightId": "r3"}]}'::jsonb,
     12, TRUE, TRUE, TRUE),
    -- ODD_ONE_OUT (EASY, base 10 -> 10)
    ('00000000-0000-0000-0000-000000011004',
     '00000000-0000-0000-0000-00000000010a', 'ODD_ONE_OUT', 'EASY', 4,
     'Melyik szó lóg ki a sorból (nem állat)?',
     '{"items": [{"id": "i1", "text": "cat"}, {"id": "i2", "text": "dog"}, {"id": "i3", "text": "book"}]}'::jsonb,
     '{"oddItemId": "i3"}'::jsonb,
     10, TRUE, TRUE, TRUE),
    -- ERROR_FINDING (EASY, base 12 -> 12)
    ('00000000-0000-0000-0000-000000011005',
     '00000000-0000-0000-0000-00000000010a', 'ERROR_FINDING', 'EASY', 5,
     'Jelöld meg a hibás szót! (a határozatlan névelő magánhangzó előtt "an")',
     '{"tokens": [{"index": 0, "text": "This"}, {"index": 1, "text": "is"}, {"index": 2, "text": "a"}, {"index": 3, "text": "apple"}]}'::jsonb,
     '{"incorrectTokenIndexes": [2]}'::jsonb,
     12, TRUE, TRUE, TRUE),
    -- TRANSLATION (AI, opcionális, max 15)
    ('00000000-0000-0000-0000-000000011006',
     '00000000-0000-0000-0000-00000000010a', 'TRANSLATION', 'EASY', 6,
     'Fordítsd le angolra: "A macska a házban van."',
     '{"sourceText": "A macska a házban van.", "targetLanguageCode": "en",
       "rubric": ["nyelvtani helyesség", "szókincs", "névelők helyes használata"]}'::jsonb,
     NULL,
     15, FALSE, FALSE, TRUE),
    -- AI_SCENARIO (AI, opcionális, max 30)
    ('00000000-0000-0000-0000-000000011007',
     '00000000-0000-0000-0000-00000000010a', 'AI_SCENARIO', 'EASY', 7,
     'Mutatkozz be egy új ismerősnek angolul!',
     '{"role": "egy új ismerős", "situation": "Egy nyelvi klubban találkoztok, bemutatkozol.",
       "goals": ["köszönj", "mondd meg a neved", "kérdezd meg a másik nevét"],
       "targetLanguageCode": "en", "openingMessage": "Hi! Nice to meet you. What is your name?"}'::jsonb,
     NULL,
     30, FALSE, FALSE, TRUE);

-- --------------------------------------------- LECKE 2 (EASY/MEDIUM) ----- --
INSERT INTO exercises
    (id, lesson_id, type, difficulty, order_index, prompt, payload, solution,
     max_points, required_for_completion, duel_eligible, enabled) VALUES
    -- MULTIPLE_CHOICE (MEDIUM, base 10 -> 15)
    ('00000000-0000-0000-0000-000000012001',
     '00000000-0000-0000-0000-00000000020a', 'MULTIPLE_CHOICE', 'MEDIUM', 1,
     'Mit jelent angolul a "blue"?',
     '{"multiSelect": false, "options": [
        {"id": "a", "text": "piros"},
        {"id": "b", "text": "zöld"},
        {"id": "c", "text": "kék"}
      ]}'::jsonb,
     '{"correctOptionIds": ["c"]}'::jsonb,
     15, TRUE, TRUE, TRUE),
    -- TRUE_FALSE (EASY, base 8 -> 8)
    ('00000000-0000-0000-0000-000000012002',
     '00000000-0000-0000-0000-00000000020a', 'TRUE_FALSE', 'EASY', 2,
     'Igaz-e az állítás?',
     '{"statement": "A \"three\" szó jelentése kettő."}'::jsonb,
     '{"answer": false}'::jsonb,
     8, TRUE, TRUE, TRUE),
    -- MATCHING (MEDIUM, base 12 -> 18)
    ('00000000-0000-0000-0000-000000012003',
     '00000000-0000-0000-0000-00000000020a', 'MATCHING', 'MEDIUM', 3,
     'Párosítsd az angol számokat a magyar jelentésükkel!',
     '{"leftItems": [{"id": "l1", "text": "one"}, {"id": "l2", "text": "two"}, {"id": "l3", "text": "three"}],
       "rightItems": [{"id": "r1", "text": "egy"}, {"id": "r2", "text": "kettő"}, {"id": "r3", "text": "három"}]}'::jsonb,
     '{"pairs": [{"leftId": "l1", "rightId": "r1"}, {"leftId": "l2", "rightId": "r2"}, {"leftId": "l3", "rightId": "r3"}]}'::jsonb,
     18, TRUE, TRUE, TRUE),
    -- ODD_ONE_OUT (MEDIUM, base 10 -> 15)
    ('00000000-0000-0000-0000-000000012004',
     '00000000-0000-0000-0000-00000000020a', 'ODD_ONE_OUT', 'MEDIUM', 4,
     'Melyik szó lóg ki a sorból (nem szín)?',
     '{"items": [{"id": "i1", "text": "red"}, {"id": "i2", "text": "green"}, {"id": "i3", "text": "two"}, {"id": "i4", "text": "blue"}]}'::jsonb,
     '{"oddItemId": "i3"}'::jsonb,
     15, TRUE, TRUE, TRUE),
    -- ERROR_FINDING (MEDIUM, base 12 -> 18)
    ('00000000-0000-0000-0000-000000012005',
     '00000000-0000-0000-0000-00000000020a', 'ERROR_FINDING', 'MEDIUM', 5,
     'Jelöld meg a hibás szavakat! (helyesen: "two red cars")',
     '{"tokens": [{"index": 0, "text": "two"}, {"index": 1, "text": "reds"}, {"index": 2, "text": "car"}]}'::jsonb,
     '{"incorrectTokenIndexes": [1, 2]}'::jsonb,
     18, TRUE, TRUE, TRUE),
    -- TRANSLATION (AI, opcionális, max 15)
    ('00000000-0000-0000-0000-000000012006',
     '00000000-0000-0000-0000-00000000020a', 'TRANSLATION', 'MEDIUM', 6,
     'Fordítsd le angolra: "Van két piros autóm."',
     '{"sourceText": "Van két piros autóm.", "targetLanguageCode": "en",
       "rubric": ["nyelvtani helyesség", "számnév és szín szórendje", "szókincs"]}'::jsonb,
     NULL,
     15, FALSE, FALSE, TRUE),
    -- AI_SCENARIO (AI, opcionális, max 30)
    ('00000000-0000-0000-0000-000000012007',
     '00000000-0000-0000-0000-00000000020a', 'AI_SCENARIO', 'MEDIUM', 7,
     'Vásárolj be egy üzletben angolul!',
     '{"role": "egy eladó", "situation": "Egy üzletben vásárolsz, az eladóval beszélsz.",
       "goals": ["köszönj", "kérdezd meg egy tárgy színét vagy árát", "köszönd meg a segítséget"],
       "targetLanguageCode": "en", "openingMessage": "Good morning! How can I help you today?"}'::jsonb,
     NULL,
     30, FALSE, FALSE, TRUE);

-- --------------------------------------------- LECKE 3 (MEDIUM) ---------- --
INSERT INTO exercises
    (id, lesson_id, type, difficulty, order_index, prompt, payload, solution,
     max_points, required_for_completion, duel_eligible, enabled) VALUES
    -- MULTIPLE_CHOICE (MEDIUM, base 10 -> 15)
    ('00000000-0000-0000-0000-000000013001',
     '00000000-0000-0000-0000-00000000030a', 'MULTIPLE_CHOICE', 'MEDIUM', 1,
     'Melyik a helyes alak: "She ___ to school every day."?',
     '{"multiSelect": false, "options": [
        {"id": "a", "text": "go"},
        {"id": "b", "text": "goes"},
        {"id": "c", "text": "going"}
      ]}'::jsonb,
     '{"correctOptionIds": ["b"]}'::jsonb,
     15, TRUE, TRUE, TRUE),
    -- TRUE_FALSE (MEDIUM, base 8 -> 12)
    ('00000000-0000-0000-0000-000000013002',
     '00000000-0000-0000-0000-00000000030a', 'TRUE_FALSE', 'MEDIUM', 2,
     'Igaz-e az állítás az egyszerű jelenről?',
     '{"statement": "Egyszerű jelenben a harmadik személyű alak \"he eats\"."}'::jsonb,
     '{"answer": true}'::jsonb,
     12, TRUE, TRUE, TRUE),
    -- MATCHING (MEDIUM, base 12 -> 18)
    ('00000000-0000-0000-0000-000000013003',
     '00000000-0000-0000-0000-00000000030a', 'MATCHING', 'MEDIUM', 3,
     'Párosítsd az igéket a harmadik személyű alakjukkal!',
     '{"leftItems": [{"id": "l1", "text": "go"}, {"id": "l2", "text": "eat"}, {"id": "l3", "text": "read"}],
       "rightItems": [{"id": "r1", "text": "goes"}, {"id": "r2", "text": "eats"}, {"id": "r3", "text": "reads"}]}'::jsonb,
     '{"pairs": [{"leftId": "l1", "rightId": "r1"}, {"leftId": "l2", "rightId": "r2"}, {"leftId": "l3", "rightId": "r3"}]}'::jsonb,
     18, TRUE, TRUE, TRUE),
    -- ODD_ONE_OUT (MEDIUM, base 10 -> 15)
    ('00000000-0000-0000-0000-000000013004',
     '00000000-0000-0000-0000-00000000030a', 'ODD_ONE_OUT', 'MEDIUM', 4,
     'Melyik szó lóg ki a sorból (nem ige)?',
     '{"items": [{"id": "i1", "text": "eat"}, {"id": "i2", "text": "read"}, {"id": "i3", "text": "blue"}]}'::jsonb,
     '{"oddItemId": "i3"}'::jsonb,
     15, TRUE, TRUE, TRUE),
    -- ERROR_FINDING (MEDIUM, base 12 -> 18)
    ('00000000-0000-0000-0000-000000013005',
     '00000000-0000-0000-0000-00000000030a', 'ERROR_FINDING', 'MEDIUM', 5,
     'Jelöld meg a hibás szót! (helyesen: "She goes home.")',
     '{"tokens": [{"index": 0, "text": "She"}, {"index": 1, "text": "go"}, {"index": 2, "text": "home"}]}'::jsonb,
     '{"incorrectTokenIndexes": [1]}'::jsonb,
     18, TRUE, TRUE, TRUE),
    -- TRANSLATION (AI, opcionális, max 15)
    ('00000000-0000-0000-0000-000000013006',
     '00000000-0000-0000-0000-00000000030a', 'TRANSLATION', 'MEDIUM', 6,
     'Fordítsd le angolra: "Minden reggel iskolába megy."',
     '{"sourceText": "Minden reggel iskolába megy.", "targetLanguageCode": "en",
       "rubric": ["harmadik személyű -s", "nyelvtani helyesség", "szókincs"]}'::jsonb,
     NULL,
     15, FALSE, FALSE, TRUE),
    -- AI_SCENARIO (AI, opcionális, max 30)
    ('00000000-0000-0000-0000-000000013007',
     '00000000-0000-0000-0000-00000000030a', 'AI_SCENARIO', 'MEDIUM', 7,
     'Mesélj a napirendedről angolul!',
     '{"role": "egy barát", "situation": "Egy barátoddal beszélgetsz a napi szokásaidról.",
       "goals": ["mondj el legalább két napi tevékenységet", "használj egyszerű jelen időt", "kérdezd meg a másik napirendjét"],
       "targetLanguageCode": "en", "openingMessage": "Hey! Tell me about your typical day."}'::jsonb,
     NULL,
     30, FALSE, FALSE, TRUE);

-- --------------------------------------------- LECKE 4 (MEDIUM/HARD) ----- --
-- A 4. lecke auto feladatai NEM duel_eligible (így összesen 15 duel_eligible
-- auto a 1-3. leckékből; a párbaj és a napi kihívás így is bőven válogathat).
INSERT INTO exercises
    (id, lesson_id, type, difficulty, order_index, prompt, payload, solution,
     max_points, required_for_completion, duel_eligible, enabled) VALUES
    -- MULTIPLE_CHOICE (HARD, base 10 -> 20)
    ('00000000-0000-0000-0000-000000014001',
     '00000000-0000-0000-0000-00000000040a', 'MULTIPLE_CHOICE', 'HARD', 1,
     'Melyik a helyes kérdő alak: "___ she speak English?"?',
     '{"multiSelect": false, "options": [
        {"id": "a", "text": "Do"},
        {"id": "b", "text": "Does"},
        {"id": "c", "text": "Is"}
      ]}'::jsonb,
     '{"correctOptionIds": ["b"]}'::jsonb,
     20, TRUE, FALSE, TRUE),
    -- TRUE_FALSE (HARD, base 8 -> 16)
    ('00000000-0000-0000-0000-000000014002',
     '00000000-0000-0000-0000-00000000040a', 'TRUE_FALSE', 'HARD', 2,
     'Igaz-e az állítás a tagadásról?',
     '{"statement": "A \"don''t\" a \"do not\" rövidített alakja."}'::jsonb,
     '{"answer": true}'::jsonb,
     16, TRUE, FALSE, TRUE),
    -- MATCHING (HARD, base 12 -> 24)
    ('00000000-0000-0000-0000-000000014003',
     '00000000-0000-0000-0000-00000000040a', 'MATCHING', 'HARD', 3,
     'Párosítsd az angol kérdéseket a magyar jelentésükkel!',
     '{"leftItems": [{"id": "l1", "text": "Do you like coffee?"}, {"id": "l2", "text": "What is your name?"}, {"id": "l3", "text": "Where do you live?"}],
       "rightItems": [{"id": "r1", "text": "Szereted a kávét?"}, {"id": "r2", "text": "Mi a neved?"}, {"id": "r3", "text": "Hol laksz?"}]}'::jsonb,
     '{"pairs": [{"leftId": "l1", "rightId": "r1"}, {"leftId": "l2", "rightId": "r2"}, {"leftId": "l3", "rightId": "r3"}]}'::jsonb,
     24, TRUE, FALSE, TRUE),
    -- ODD_ONE_OUT (HARD, base 10 -> 20)
    ('00000000-0000-0000-0000-000000014004',
     '00000000-0000-0000-0000-00000000040a', 'ODD_ONE_OUT', 'HARD', 4,
     'Melyik szó lóg ki a sorból (nem kérdőszó)?',
     '{"items": [{"id": "i1", "text": "what"}, {"id": "i2", "text": "where"}, {"id": "i3", "text": "coffee"}, {"id": "i4", "text": "who"}]}'::jsonb,
     '{"oddItemId": "i3"}'::jsonb,
     20, TRUE, FALSE, TRUE),
    -- ERROR_FINDING (HARD, base 12 -> 24)
    ('00000000-0000-0000-0000-000000014005',
     '00000000-0000-0000-0000-00000000040a', 'ERROR_FINDING', 'HARD', 5,
     'Jelöld meg a hibás szót! (helyesen: "Does she speak English?")',
     '{"tokens": [{"index": 0, "text": "Do"}, {"index": 1, "text": "she"}, {"index": 2, "text": "speak"}, {"index": 3, "text": "English"}]}'::jsonb,
     '{"incorrectTokenIndexes": [0]}'::jsonb,
     24, TRUE, FALSE, TRUE),
    -- TRANSLATION (AI, opcionális, max 15)
    ('00000000-0000-0000-0000-000000014006',
     '00000000-0000-0000-0000-00000000040a', 'TRANSLATION', 'HARD', 6,
     'Fordítsd le angolra: "Beszélsz angolul?"',
     '{"sourceText": "Beszélsz angolul?", "targetLanguageCode": "en",
       "rubric": ["kérdő szórend", "segédige használata", "nyelvtani helyesség"]}'::jsonb,
     NULL,
     15, FALSE, FALSE, TRUE),
    -- AI_SCENARIO (AI, opcionális, max 30)
    ('00000000-0000-0000-0000-000000014007',
     '00000000-0000-0000-0000-00000000040a', 'AI_SCENARIO', 'HARD', 7,
     'Rendelj egy kávézóban angolul!',
     '{"role": "egy pincér", "situation": "Egy kávézóban ülsz, a pincér felveszi a rendelésedet.",
       "goals": ["köszönj", "rendelj egy italt", "kérdezd meg az árat", "köszönd meg"],
       "targetLanguageCode": "en", "openingMessage": "Welcome! What can I get for you?"}'::jsonb,
     NULL,
     30, FALSE, FALSE, TRUE);
