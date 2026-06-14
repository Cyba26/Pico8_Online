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
initDB().then(() => {
  app.listen(PORT, () => console.log(`[Pico8-Online] Server on port ${PORT}`));
}).catch(err => {
  console.error('DB init failed:', err);
  process.exit(1);
});
