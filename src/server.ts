import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import multer from 'multer';
import pg from 'pg';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const app = express();
const PORT = parseInt(process.env.PORT || '3000', 10);
const UPLOADS_DIR = process.env.UPLOADS_DIR || path.join(__dirname, '../uploads');
const PUBLIC_DIR = path.join(__dirname, '../public');

// Ensure upload directories exist
for (const d of ['runtime', 'carts', 'thumbs']) {
  fs.mkdirSync(path.join(UPLOADS_DIR, d), { recursive: true });
}

// ── DB ──────────────────────────────────────────────────────────────────────
const pool = new pg.Pool({
  connectionString: process.env.DATABASE_URL,
  ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : false,
});

async function initDB() {
  await pool.query(`
    CREATE TABLE IF NOT EXISTS cartridges (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      name TEXT NOT NULL,
      cart_path TEXT NOT NULL,
      thumb_path TEXT,
      category TEXT,
      position INTEGER,
      created_at TIMESTAMPTZ DEFAULT NOW()
    );
    ALTER TABLE cartridges ADD COLUMN IF NOT EXISTS position INTEGER;
    CREATE TABLE IF NOT EXISTS leaderboard (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      game_name TEXT NOT NULL,
      player_name TEXT NOT NULL,
      score INTEGER NOT NULL,
      "Difficulty" TEXT DEFAULT 'Easy',
      session_id UUID,
      created_at TIMESTAMPTZ DEFAULT NOW()
    );
    CREATE TABLE IF NOT EXISTS game_sessions (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      game_name TEXT NOT NULL,
      used BOOLEAN DEFAULT FALSE,
      created_at TIMESTAMPTZ DEFAULT NOW()
    );
  `);
}

// One-time seed of historical Shumpy Jump scores (idempotent via ON CONFLICT).
// Imported from leaderboard_rows.csv.
const SEED_SCORES: [string, string, string, number, string][] = [
  ['00c01250-8e5c-4d64-b2da-008868aea29e', 'shumpy_jump', 'Duncan', 7840, 'Easy'],
  ['12bdcf7f-987a-41f3-9941-c22a78abf7b0', 'shumpy_jump', 'Duncan', 12680, 'Easy'],
  ['16cdd692-6b9b-47b8-9b98-c39aaff795cf', 'shumpy_jump', 'Cyba', 14020, 'Easy'],
  ['1d1eb22b-3400-49e0-b8de-7e97acb46815', 'shumpy_jump', 'Duncan', 8760, 'Easy'],
  ['225895fe-611e-46bb-bdea-573a257006ef', 'shumpy_jump', 'Cyba', 3750, 'Easy'],
  ['2810d5b7-5716-49a7-ad76-a1792c2d14a0', 'shumpy_jump', 'Duncan', 14490, 'Easy'],
  ['29731d12-fbc9-4174-9f5f-c8d526a616f3', 'shumpy_jump', 'Tang', 14350, 'Hard'],
  ['5bcdcc81-d9ee-497d-bf76-57d810524e86', 'shumpy_jump', 'Duncan', 28010, 'Hard'],
  ['5db4b663-89fe-4bb9-baf9-e54a05ccdbf2', 'shumpy_jump', 'Duncan', 13150, 'Nightmare'],
  ['68ba0f41-9941-484b-ac9b-c0ced07a3d26', 'shumpy_jump', 'Tang', 10620, 'Nightmare'],
  ['7214a550-0303-43b9-bb9d-0d3b32db92fa', 'shumpy_jump', 'Cyba', 21140, 'Nightmare'],
  ['783af7a6-f45d-42c9-a3af-70b5237dc6ba', 'shumpy_jump', 'Cyba', 27780, 'Hard'],
  ['86733488-7062-4d0b-873b-29d687cf9a73', 'shumpy_jump', 'Duncan', 13820, 'Easy'],
  ['8f449b52-6b87-4767-8756-28256ea74dfb', 'shumpy_jump', 'Duncan', 16690, 'Easy'],
  ['a4095fd9-4b95-483a-8941-38c65a5bf5cd', 'shumpy_jump', 'Cyba', 13040, 'Nightmare'],
  ['ab01a4b2-7927-44e8-bb3c-51e2971d3bf1', 'shumpy_jump', 'Cyba', 20010, 'Hard'],
  ['ad47e5cb-7535-4106-a85e-47420bb8f806', 'shumpy_jump', 'Duncan', 13540, 'Easy'],
  ['afdf278f-e579-444e-bc0c-a498aaa713fc', 'shumpy_jump', 'Cyba', 640, 'Hard'],
  ['b4c0a495-a398-4c8d-80e7-ea792b16b570', 'shumpy_jump', 'Tang', 15510, 'Easy'],
  ['b739147b-8555-4b1f-b1d2-7a6bcdf12781', 'shumpy_jump', 'Quentin', 5510, 'Easy'],
  ['b8899e58-e2bf-419f-8d1b-b65a266c15eb', 'shumpy_jump', 'Duncan', 17860, 'Hard'],
  ['de2cdb31-ddde-4952-9c15-5cc701559ed4', 'shumpy_jump', 'Cyba', 30910, 'Hard'],
  ['e38c45f6-40ba-4103-a257-600c1e882a65', 'shumpy_jump', 'Carzy', 3450, 'Easy'],
  ['f079f765-a810-43df-b653-63b8868c5136', 'shumpy_jump', 'Duncan', 2760, 'Nightmare'],
];

async function seedLeaderboard() {
  for (const [id, game, player, score, diff] of SEED_SCORES) {
    await pool.query(
      `INSERT INTO leaderboard (id, game_name, player_name, score, "Difficulty")
       VALUES ($1, $2, $3, $4, $5) ON CONFLICT (id) DO NOTHING`,
      [id, game, player, score, diff],
    );
  }
}

// ── MIDDLEWARE ───────────────────────────────────────────────────────────────
app.use(helmet({ contentSecurityPolicy: false })); // CSP off — frontend uses blob: URLs
app.use(express.json());
app.use(cors({ origin: true, credentials: true }));
app.use('/uploads', express.static(UPLOADS_DIR));
app.use(express.static(PUBLIC_DIR));

// ── MULTER ───────────────────────────────────────────────────────────────────
const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    const dir = file.fieldname === 'runtime' ? 'runtime'
               : file.fieldname === 'thumb'   ? 'thumbs'
               : 'carts';
    cb(null, path.join(UPLOADS_DIR, dir));
  },
  filename: (req, file, cb) => {
    if (file.fieldname === 'runtime') {
      cb(null, 'pico8.dat');
    } else {
      cb(null, `${Date.now()}_${Math.random().toString(36).slice(2, 10)}.png`);
    }
  },
});
const upload = multer({ storage });

// ── RUNTIME ──────────────────────────────────────────────────────────────────
app.get('/api/runtime/check', (_req, res) => {
  const exists = fs.existsSync(path.join(UPLOADS_DIR, 'runtime', 'pico8.dat'));
  res.json({ exists, url: exists ? '/uploads/runtime/pico8.dat' : null });
});

app.post('/api/runtime', upload.single('runtime'), (req, res) => {
  if (!req.file) { res.status(400).json({ error: 'No file' }); return; }
  res.json({ success: true });
});

// ── CARTRIDGES ───────────────────────────────────────────────────────────────
function cartRow(r: Record<string, unknown>) {
  return {
    ...r,
    cart_url: `/uploads/carts/${path.basename(r.cart_path as string)}`,
    thumb_url: r.thumb_path ? `/uploads/thumbs/${path.basename(r.thumb_path as string)}` : null,
  };
}

app.get('/api/cartridges', async (_req, res) => {
  const { rows } = await pool.query('SELECT * FROM cartridges ORDER BY position ASC NULLS LAST, created_at ASC');
  res.json(rows.map(cartRow));
});

// Bulk reorder + recategorize (drag & drop). Body: { order: [{ id, category, position }] }
app.post('/api/cartridges/reorder', async (req, res) => {
  const order = (req.body?.order ?? []) as { id: string; category: string | null; position: number }[];
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    for (const item of order) {
      await client.query(
        'UPDATE cartridges SET position = $1, category = $2 WHERE id = $3',
        [item.position, item.category ?? null, item.id],
      );
    }
    await client.query('COMMIT');
    res.json({ success: true });
  } catch (e) {
    await client.query('ROLLBACK');
    res.status(500).json({ error: 'Reorder failed' });
  } finally {
    client.release();
  }
});

app.post('/api/cartridges', upload.fields([
  { name: 'cart', maxCount: 1 },
  { name: 'thumb', maxCount: 1 },
]), async (req, res) => {
  const files = req.files as Record<string, Express.Multer.File[]>;
  const cartFile = files['cart']?.[0];
  const thumbFile = files['thumb']?.[0];
  if (!cartFile) { res.status(400).json({ error: 'No cartridge file' }); return; }
  const { rows } = await pool.query(
    `INSERT INTO cartridges (name, cart_path, thumb_path, category, position)
     VALUES ($1, $2, $3, $4, COALESCE((SELECT MAX(position) + 1 FROM cartridges), 0))
     RETURNING *`,
    [req.body.name, cartFile.path, thumbFile?.path ?? null, req.body.category ?? null],
  );
  res.json(cartRow(rows[0]));
});

app.patch('/api/cartridges/:id', async (req, res) => {
  await pool.query('UPDATE cartridges SET category = $1 WHERE id = $2', [req.body.category, req.params.id]);
  res.json({ success: true });
});

app.delete('/api/cartridges/:id', async (req, res) => {
  const { rows } = await pool.query('SELECT * FROM cartridges WHERE id = $1', [req.params.id]);
  if (!rows[0]) { res.status(404).json({ error: 'Not found' }); return; }
  for (const p of [rows[0].cart_path, rows[0].thumb_path].filter(Boolean)) {
    try { fs.unlinkSync(p as string); } catch {}
  }
  await pool.query('DELETE FROM cartridges WHERE id = $1', [req.params.id]);
  res.json({ success: true });
});

// ── CATEGORIES ───────────────────────────────────────────────────────────────
const CAT_FILE = path.join(UPLOADS_DIR, 'categories.json');

app.get('/api/categories', (_req, res) => {
  if (fs.existsSync(CAT_FILE)) {
    res.json(JSON.parse(fs.readFileSync(CAT_FILE, 'utf8')));
  } else {
    res.json(['Mes cartouches', 'Autres cartouches']);
  }
});

app.put('/api/categories', (req, res) => {
  fs.writeFileSync(CAT_FILE, JSON.stringify(req.body));
  res.json({ success: true });
});

// ── LEADERBOARD ──────────────────────────────────────────────────────────────
app.get('/api/leaderboard/:gameName', async (req, res) => {
  // Match on a normalized name (lowercase, alphanumerics only) so any spelling
  // of the cartridge ("Shumpy Jump", "shumpy_jump", …) maps to the same board.
  const norm = req.params.gameName.toLowerCase().replace(/[^a-z0-9]/g, '');
  const { rows } = await pool.query(
    `SELECT * FROM leaderboard
     WHERE regexp_replace(lower(game_name), '[^a-z0-9]', '', 'g') = $1
     ORDER BY score DESC LIMIT 100`,
    [norm],
  );
  res.json(rows);
});

app.delete('/api/scores/:id', async (req, res) => {
  await pool.query('DELETE FROM leaderboard WHERE id = $1', [req.params.id]);
  res.json({ success: true });
});

app.post('/api/sessions', async (req, res) => {
  const { rows } = await pool.query(
    'INSERT INTO game_sessions (game_name) VALUES ($1) RETURNING id',
    [req.body.game_name],
  );
  res.json({ id: rows[0].id });
});

app.post('/api/scores', async (req, res) => {
  const { session_id, player_name, score, difficulty } = req.body as {
    session_id: string; player_name: string; score: number; difficulty: string;
  };
  const { rows } = await pool.query(
    'SELECT * FROM game_sessions WHERE id = $1 AND used = false',
    [session_id],
  );
  if (!rows[0]) { res.status(400).json({ error: 'Invalid or used session' }); return; }
  await pool.query('UPDATE game_sessions SET used = true WHERE id = $1', [session_id]);
  await pool.query(
    'INSERT INTO leaderboard (game_name, player_name, score, "Difficulty", session_id) VALUES ($1, $2, $3, $4, $5)',
    [rows[0].game_name, player_name, score, difficulty || 'Easy', session_id],
  );
  res.json({ success: true });
});

// ── SPA FALLBACK ─────────────────────────────────────────────────────────────
app.get('*', (_req, res) => {
  res.sendFile(path.join(PUBLIC_DIR, 'index.html'));
});

// ── START ─────────────────────────────────────────────────────────────────────
initDB()
  .then(() => seedLeaderboard())
  .then(() => {
    app.listen(PORT, () => console.log(`[Pico8-Online] Server on port ${PORT}`));
  })
  .catch(err => {
    console.error('DB init failed:', err);
    process.exit(1);
  });
