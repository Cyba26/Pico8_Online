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

-- V8: Score validation via game sessions + fix leaderboard delete policy
-- Remove direct anon INSERT on leaderboard (scores go through RPC now)
DROP POLICY IF EXISTS "Anon insert validated" ON leaderboard;
-- Remove anon DELETE on leaderboard (admin only)
DROP POLICY IF EXISTS "Anon delete" ON leaderboard;
CREATE POLICY "Auth delete" ON leaderboard FOR DELETE USING (auth.role() = 'authenticated');

-- Game sessions table (links a game launch to a score submission)
CREATE TABLE game_sessions (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  game_name TEXT NOT NULL,
  started_at TIMESTAMPTZ DEFAULT now(),
  used BOOLEAN DEFAULT false
);
ALTER TABLE game_sessions ENABLE ROW LEVEL SECURITY;
-- No direct access for anon — only through RPC functions

-- RPC: Start a game session (returns session UUID)
CREATE OR REPLACE FUNCTION start_game_session(p_game_name TEXT)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  session_id UUID;
BEGIN
  INSERT INTO game_sessions (game_name)
  VALUES (p_game_name)
  RETURNING id INTO session_id;
  RETURN session_id;
END;
$$;

-- RPC: Submit a validated score
CREATE OR REPLACE FUNCTION submit_game_score(
  p_session_id UUID,
  p_player_name TEXT,
  p_score INT,
  p_difficulty TEXT DEFAULT 'Easy'
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  sess RECORD;
BEGIN
  -- Fetch and lock the session
  SELECT * INTO sess
  FROM game_sessions
  WHERE id = p_session_id AND used = false
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Invalid or already used game session';
  END IF;

  -- Minimum play time: 5 seconds
  IF now() - sess.started_at < INTERVAL '5 seconds' THEN
    RAISE EXCEPTION 'Game session too short';
  END IF;

  -- Validate inputs
  IF p_score <= 0 OR p_score >= 200000 THEN
    RAISE EXCEPTION 'Invalid score';
  END IF;
  IF length(p_player_name) = 0 OR length(p_player_name) > 20 THEN
    RAISE EXCEPTION 'Invalid player name';
  END IF;
  IF p_player_name LIKE '%<%' OR p_player_name LIKE '%>%' OR p_player_name LIKE '%&%' THEN
    RAISE EXCEPTION 'Invalid characters in player name';
  END IF;

  -- Mark session as used
  UPDATE game_sessions SET used = true WHERE id = p_session_id;

  -- Insert the score
  INSERT INTO leaderboard (game_name, player_name, score, "Difficulty")
  VALUES (sess.game_name, p_player_name, p_score, p_difficulty);
END;
$$;
