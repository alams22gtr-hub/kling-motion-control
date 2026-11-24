import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import multer from 'multer';
import dotenv from 'dotenv';
import path from 'node:path';
import fs from 'node:fs';
import crypto from 'node:crypto';

dotenv.config();
const app = express();
const PORT = Number(process.env.PORT || 3000);
const KEY = process.env.KLING_API_KEY;
const ACCESS_TOKEN = process.env.ACCESS_TOKEN || '';
const PUBLIC_BASE_URL = (process.env.PUBLIC_BASE_URL || '').replace(/\/$/, '');
const uploadDir = path.resolve('uploads');
fs.mkdirSync(uploadDir, { recursive: true });

app.use(helmet({ crossOriginResourcePolicy: { policy: 'cross-origin' } }));
app.use(cors());
app.use(express.json({ limit: '1mb' }));
app.use('/uploads', express.static(uploadDir, { fallthrough: false, maxAge: '1d' }));

// Basic personal-use gate. This is not a substitute for production user auth.
app.use('/api', (req, res, next) => {
  if (!ACCESS_TOKEN) return res.status(503).json({ error: 'ACCESS_TOKEN belum dikonfigurasi pada backend.' });
  const supplied = (req.get('authorization') || '').replace(/^Bearer\s+/i, '');
  const a = Buffer.from(supplied);
  const b = Buffer.from(ACCESS_TOKEN);
  if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) {
    return res.status(401).json({ error: 'Token aplikasi tidak valid.' });
  }
  next();
});

const storage = multer.diskStorage({
  destination: (_req, _file, cb) => cb(null, uploadDir),
  filename: (_req, file, cb) => {
    const ext = path.extname(file.originalname).toLowerCase();
    cb(null, `${Date.now()}-${crypto.randomBytes(8).toString('hex')}${ext}`);
  }
});
const upload = multer({
  storage,
  limits: { fileSize: 100 * 1024 * 1024 },
  fileFilter: (_req, file, cb) => {
    const ext = path.extname(file.originalname).toLowerCase();
    const image = ['.jpg', '.jpeg', '.png'].includes(ext) && file.mimetype.startsWith('image/');
    const video = ['.mp4', '.mov'].includes(ext) && file.mimetype.startsWith('video/');
    cb(image || video ? null : new Error('Format tidak didukung. Gunakan JPG/PNG atau MP4/MOV.'), image || video);
  }
});

app.get('/health', (_req, res) => res.json({ ok: true, service: 'kling-motion-backend' }));

app.post('/api/upload', upload.single('file'), (req, res) => {
  if (!PUBLIC_BASE_URL) return res.status(500).json({ error: 'PUBLIC_BASE_URL belum diatur pada backend.' });
  if (!req.file) return res.status(400).json({ error: 'File tidak ditemukan.' });
  const url = `${PUBLIC_BASE_URL}/uploads/${encodeURIComponent(req.file.filename)}`;
  res.json({ url, filename: req.file.filename, size: req.file.size, mimeType: req.file.mimetype });
});

app.post('/api/motion', async (req, res) => {
  try {
    if (!KEY) return res.status(500).json({ error: 'KLING_API_KEY belum diatur pada backend.' });
    const { imageUrl, videoUrl, prompt = '', characterOrientation = 'video', resolution = '1080p', audio = 'original' } = req.body || {};
    if (!imageUrl || !videoUrl) return res.status(400).json({ error: 'imageUrl dan videoUrl wajib diisi.' });
    if (!['image', 'video'].includes(characterOrientation)) return res.status(400).json({ error: 'characterOrientation harus image atau video.' });
    if (!['720p', '1080p'].includes(resolution)) return res.status(400).json({ error: 'resolution harus 720p atau 1080p.' });
    if (!['original', 'off'].includes(audio)) return res.status(400).json({ error: 'audio harus original atau off.' });
    const contents = [];
    if (prompt.trim()) contents.push({ type: 'prompt', text: prompt.trim().slice(0, 2500) });
    contents.push({ type: 'image', url: imageUrl }, { type: 'video', url: videoUrl });

    const kling = await fetch('https://api-singapore.klingai.com/motion-control/kling-3.0', {
      method: 'POST',
      headers: { Authorization: `Bearer ${KEY}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ contents, settings: { character_orientation: characterOrientation, resolution, audio } })
    });
    const data = await kling.json().catch(() => ({}));
    if (!kling.ok || data.code !== 0) return res.status(kling.status || 502).json({ error: data.message || 'Kling API gagal membuat task.', kling: data });
    res.json(data);
  } catch (err) {
    res.status(500).json({ error: err.message || 'Kesalahan server.' });
  }
});

app.get('/api/tasks/:taskId', async (req, res) => {
  try {
    if (!KEY) return res.status(500).json({ error: 'KLING_API_KEY belum diatur pada backend.' });
    const id = encodeURIComponent(req.params.taskId);
    const kling = await fetch(`https://api-singapore.klingai.com/tasks?task_ids=${id}`, {
      headers: { Authorization: `Bearer ${KEY}`, 'Content-Type': 'application/json' }
    });
    const data = await kling.json().catch(() => ({}));
    res.status(kling.status).json(data);
  } catch (err) { res.status(500).json({ error: err.message || 'Kesalahan server.' }); }
});

app.use((err, _req, res, _next) => res.status(400).json({ error: err.message || 'Permintaan tidak valid.' }));
app.listen(PORT, '0.0.0.0', () => console.log(`Kling backend listening on ${PORT}`));
