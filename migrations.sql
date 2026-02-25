-- V1: Cartridges table
CREATE TABLE cartridges (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  name TEXT NOT NULL,
  thumb_url TEXT,
  cart_url TEXT NOT NULL,
  created_at TIMESTAMPTZ DEFAULT now()
);
ALTER TABLE cartridges ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Public read" ON cartridges FOR SELECT USING (true);
CREATE POLICY "Anon upload" ON storage.objects FOR INSERT WITH CHECK (bucket_id = 'pico8');
CREATE POLICY "Anon delete" ON storage.objects FOR DELETE USING (bucket_id = 'pico8');
CREATE POLICY "Public read" ON storage.objects FOR SELECT USING (bucket_id = 'pico8');
CREATE POLICY "Anon insert" ON cartridges FOR INSERT WITH CHECK (true);
CREATE POLICY "Anon delete" ON cartridges FOR DELETE USING (true);

-- V2: Storage bucket
INSERT INTO storage.buckets (id, name, public) VALUES ('pico8', 'pico8', true);

-- V3: Leaderboard
CREATE TABLE leaderboard (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  game_name TEXT NOT NULL,
  player_name TEXT NOT NULL,
  score INTEGER NOT NULL,
  "Difficulty" TEXT,
  created_at TIMESTAMPTZ DEFAULT now()
);
ALTER TABLE leaderboard ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Public read" ON leaderboard FOR SELECT USING (true);

-- V4: Leaderboard validation + delete
DROP POLICY IF EXISTS "Anon insert" ON leaderboard;
CREATE POLICY "Anon insert validated" ON leaderboard
FOR INSERT WITH CHECK (
  score > 0 AND score < 200000
  AND length(player_name) > 0
  AND length(player_name) <= 20
);
CREATE POLICY "Anon delete" ON leaderboard FOR DELETE USING (true);

-- V5: Block HTML chars in player_name (defense in depth against XSS)
DROP POLICY IF EXISTS "Anon insert validated" ON leaderboard;
CREATE POLICY "Anon insert validated" ON leaderboard
FOR INSERT WITH CHECK (
  score > 0 AND score < 200000
  AND length(player_name) > 0
  AND length(player_name) <= 20
  AND player_name NOT LIKE '%<%'
  AND player_name NOT LIKE '%>%'
  AND player_name NOT LIKE '%&%'
);

-- V6: Cartridge categories
ALTER TABLE cartridges ADD COLUMN IF NOT EXISTS category TEXT DEFAULT 'Autres cartouches';
CREATE POLICY "Anon update" ON cartridges FOR UPDATE USING (true) WITH CHECK (true);

-- V7: Restrict writes to authenticated users (Supabase Auth)
-- Drop all anon write policies on cartridges + storage
DROP POLICY IF EXISTS "Anon insert" ON cartridges;
DROP POLICY IF EXISTS "Anon delete" ON cartridges;
DROP POLICY IF EXISTS "Anon update" ON cartridges;
DROP POLICY IF EXISTS "Anon upload" ON storage.objects;
DROP POLICY IF EXISTS "Anon delete" ON storage.objects;
-- Re-create as authenticated-only
CREATE POLICY "Auth insert" ON cartridges FOR INSERT WITH CHECK (auth.role() = 'authenticated');
CREATE POLICY "Auth delete" ON cartridges FOR DELETE USING (auth.role() = 'authenticated');
CREATE POLICY "Auth update" ON cartridges FOR UPDATE USING (auth.role() = 'authenticated') WITH CHECK (auth.role() = 'authenticated');
CREATE POLICY "Auth upload" ON storage.objects FOR INSERT WITH CHECK (bucket_id = 'pico8' AND auth.role() = 'authenticated');
CREATE POLICY "Auth delete storage" ON storage.objects FOR DELETE USING (bucket_id = 'pico8' AND auth.role() = 'authenticated');
