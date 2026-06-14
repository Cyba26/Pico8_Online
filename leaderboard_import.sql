-- Import des scores Shumpy Jump (depuis leaderboard_rows.csv)
-- À exécuter une fois dans Railway → service Postgres → onglet "Data" / "Query".
-- ON CONFLICT (id) DO NOTHING : ré-exécutable sans créer de doublons.

INSERT INTO leaderboard (id, game_name, player_name, score, "Difficulty") VALUES
('00c01250-8e5c-4d64-b2da-008868aea29e','shumpy_jump','Duncan',7840,'Easy'),
('12bdcf7f-987a-41f3-9941-c22a78abf7b0','shumpy_jump','Duncan',12680,'Easy'),
('16cdd692-6b9b-47b8-9b98-c39aaff795cf','shumpy_jump','Cyba',14020,'Easy'),
('1d1eb22b-3400-49e0-b8de-7e97acb46815','shumpy_jump','Duncan',8760,'Easy'),
('225895fe-611e-46bb-bdea-573a257006ef','shumpy_jump','Cyba',3750,'Easy'),
('2810d5b7-5716-49a7-ad76-a1792c2d14a0','shumpy_jump','Duncan',14490,'Easy'),
('29731d12-fbc9-4174-9f5f-c8d526a616f3','shumpy_jump','Tang',14350,'Hard'),
('5bcdcc81-d9ee-497d-bf76-57d810524e86','shumpy_jump','Duncan',28010,'Hard'),
('5db4b663-89fe-4bb9-baf9-e54a05ccdbf2','shumpy_jump','Duncan',13150,'Nightmare'),
('68ba0f41-9941-484b-ac9b-c0ced07a3d26','shumpy_jump','Tang',10620,'Nightmare'),
('7214a550-0303-43b9-bb9d-0d3b32db92fa','shumpy_jump','Cyba',21140,'Nightmare'),
('783af7a6-f45d-42c9-a3af-70b5237dc6ba','shumpy_jump','Cyba',27780,'Hard'),
('86733488-7062-4d0b-873b-29d687cf9a73','shumpy_jump','Duncan',13820,'Easy'),
('8f449b52-6b87-4767-8756-28256ea74dfb','shumpy_jump','Duncan',16690,'Easy'),
('a4095fd9-4b95-483a-8941-38c65a5bf5cd','shumpy_jump','Cyba',13040,'Nightmare'),
('ab01a4b2-7927-44e8-bb3c-51e2971d3bf1','shumpy_jump','Cyba',20010,'Hard'),
('ad47e5cb-7535-4106-a85e-47420bb8f806','shumpy_jump','Duncan',13540,'Easy'),
('afdf278f-e579-444e-bc0c-a498aaa713fc','shumpy_jump','Cyba',640,'Hard'),
('b4c0a495-a398-4c8d-80e7-ea792b16b570','shumpy_jump','Tang',15510,'Easy'),
('b739147b-8555-4b1f-b1d2-7a6bcdf12781','shumpy_jump','Quentin',5510,'Easy'),
('b8899e58-e2bf-419f-8d1b-b65a266c15eb','shumpy_jump','Duncan',17860,'Hard'),
('de2cdb31-ddde-4952-9c15-5cc701559ed4','shumpy_jump','Cyba',30910,'Hard'),
('e38c45f6-40ba-4103-a257-600c1e882a65','shumpy_jump','Carzy',3450,'Easy'),
('f079f765-a810-43df-b653-63b8868c5136','shumpy_jump','Duncan',2760,'Nightmare')
ON CONFLICT (id) DO NOTHING;
