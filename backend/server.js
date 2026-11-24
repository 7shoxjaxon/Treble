// Treble serveri: hisoblar, o'yinlar, band qilish, izohlar, kuponlar, rasmlar va SMS tasdiqlash.
// Qo'shimcha paket kerak emas: faqat Node.js 22 yoki yangisi (node server.js).

'use strict';

const http = require('http');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const { DatabaseSync } = require('node:sqlite');

// ================================================================ Sozlamalar (.env)

function loadEnvFile() {
  const file = path.join(__dirname, '.env');
  if (!fs.existsSync(file)) return;
  for (const raw of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith('#')) continue;
    const i = line.indexOf('=');
    if (i === -1) continue;
    const key = line.slice(0, i).trim();
    let val = line.slice(i + 1).trim();
    if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) val = val.slice(1, -1);
    if (process.env[key] === undefined) process.env[key] = val;
  }
}
loadEnvFile();

const bool = (v, d) => (v === undefined || v === '' ? d : String(v).toLowerCase() === 'true');

const PORT = parseInt(process.env.PORT || '3000', 10);
const DEV_MODE = bool(process.env.DEV_MODE, true);          // true: SMS yuborilmaydi, kod konsolda ko'rinadi
const OTP_REQUIRED = bool(process.env.OTP_REQUIRED, true);  // false: ro'yxatdan o'tishda SMS so'ralmaydi
const TRUST_PROXY = bool(process.env.TRUST_PROXY, false);
const ESKIZ_EMAIL = process.env.ESKIZ_EMAIL || '';
const ESKIZ_PASSWORD = process.env.ESKIZ_PASSWORD || '';
const ESKIZ_FROM = process.env.ESKIZ_FROM || '4546';
const SMS_TEMPLATE = process.env.SMS_TEMPLATE || 'Treble ilovasi uchun tasdiqlash kodi: {code}';
// Telegram Gateway (gateway.telegram.org): token berilsa, tasdiqlash kodi SMS o'rniga Telegram orqali yuboriladi.
const TELEGRAM_GATEWAY_TOKEN = process.env.TELEGRAM_GATEWAY_TOKEN || '';
// Telegram bot + Mini App (iOS foydalanuvchilar uchun): @BotFather tokeni va Mini App manzili (https://.../app)
const TELEGRAM_BOT_TOKEN = process.env.TELEGRAM_BOT_TOKEN || '';
const MINIAPP_URL = process.env.MINIAPP_URL || '';
// Telegram bot orqali kod yuborish (bepul): foydalanuvchi botga raqamini ulashadi, kod shu bot orqali keladi.
let BOT_USERNAME = (process.env.TELEGRAM_BOT_USERNAME || '').replace(/^@/, '');
const OTP_VIA_BOT = !!TELEGRAM_BOT_TOKEN && (process.env.OTP_CHANNEL === 'bot' || !TELEGRAM_GATEWAY_TOKEN);
const BOT_PENDING_TTL_MS = 10 * 60 * 1000; // foydalanuvchi botga o'tib raqamini ulashguncha kutish vaqti
const DATA_DIR = path.resolve(process.env.DATA_DIR || path.join(__dirname, 'data'));
const DB_PATH = process.env.DB_PATH || path.join(DATA_DIR, 'treble.db');
const UPLOAD_DIR = process.env.UPLOAD_DIR || path.join(DATA_DIR, 'uploads');

// Optional bootstrap administrator. Never embed real credentials in source.
const SEED_ADMINS = [];
const bootstrapLogin = process.env.ADMIN_LOGIN || '';
const bootstrapPassword = process.env.ADMIN_PASSWORD || '';
if (bootstrapLogin || bootstrapPassword) {
  if (!/^[A-Za-z0-9_.]{3,32}$/.test(bootstrapLogin) ||
      bootstrapPassword.length < 12 || bootstrapPassword.length > 64 ||
      bootstrapPassword.toLowerCase() === bootstrapLogin.toLowerCase()) {
    throw new Error('Set ADMIN_LOGIN and a unique ADMIN_PASSWORD (12-64 characters).');
  }
  SEED_ADMINS.push({ login: bootstrapLogin, name: process.env.ADMIN_NAME || 'Administrator', password: bootstrapPassword });
}
const HOST = process.env.HOST || '127.0.0.1';
if (DEV_MODE && !['127.0.0.1', '::1', 'localhost'].includes(HOST)) {
  throw new Error('DEV_MODE may only listen on a loopback address.');
}
if (!DEV_MODE && (!OTP_REQUIRED || !process.env.OTP_SECRET || process.env.OTP_SECRET.length < 32)) {
  throw new Error('Non-development mode requires OTP_REQUIRED=true and a strong OTP_SECRET.');
}

let SECRET = process.env.OTP_SECRET || '';
if (!SECRET) {
  SECRET = crypto.randomBytes(32).toString('hex');
  console.warn("[!] OTP_SECRET berilmagan: vaqtinchalik kalit yaratildi (server qayta ishga tushsa o'zgaradi).");
}

const TZ_OFFSET_MS = 5 * 3600 * 1000; // Toshkent vaqti (UTC+5)
const CODE_TTL_MS = 3 * 60 * 1000;
const RESEND_COOLDOWN_MS = 60 * 1000;
const MAX_SENDS_PER_HOUR_PHONE = 5;
const MAX_SENDS_PER_HOUR_IP = 20;
const MAX_ATTEMPTS = 5;
const TOKEN_TTL_MS = 15 * 60 * 1000;
const SESSION_TTL_MS = 90 * 24 * 3600 * 1000;
const MAX_UPLOAD_BYTES = 6 * 1024 * 1024;
const UZ_MOBILE_PREFIXES = ['20', '33', '50', '55', '77', '88', '90', '91', '93', '94', '95', '97', '98', '99'];

fs.mkdirSync(DATA_DIR, { recursive: true });
fs.mkdirSync(UPLOAD_DIR, { recursive: true });

// ================================================================ Baza (SQLite)

const db = new DatabaseSync(DB_PATH);
db.exec(`
PRAGMA journal_mode = WAL;
CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY, name TEXT NOT NULL, phone TEXT UNIQUE, login TEXT NOT NULL UNIQUE COLLATE NOCASE,
  pass_hash TEXT NOT NULL, salt TEXT NOT NULL, role TEXT NOT NULL DEFAULT 'USER', joined_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS sessions (
  token_hash TEXT PRIMARY KEY, user_id TEXT NOT NULL, created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions(user_id);
CREATE TABLE IF NOT EXISTS games (
  id TEXT PRIMARY KEY, venue_name TEXT NOT NULL, address TEXT NOT NULL DEFAULT '', map_link TEXT NOT NULL DEFAULT '',
  images TEXT NOT NULL DEFAULT '[]', date_millis INTEGER NOT NULL, start_time TEXT NOT NULL, duration_min INTEGER NOT NULL,
  format TEXT NOT NULL, max_players INTEGER NOT NULL, price INTEGER NOT NULL, description TEXT NOT NULL DEFAULT '',
  rules TEXT NOT NULL DEFAULT '', pay_info TEXT NOT NULL DEFAULT '', free_cancel_hours INTEGER NOT NULL DEFAULT 24,
  is_finished INTEGER NOT NULL DEFAULT 0, is_cancelled INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS players (
  game_id TEXT NOT NULL, id TEXT NOT NULL, name TEXT NOT NULL, phone TEXT NOT NULL DEFAULT '',
  attended INTEGER, paid INTEGER NOT NULL DEFAULT 0, pay_method TEXT NOT NULL DEFAULT 'CASH', booked_at INTEGER NOT NULL,
  PRIMARY KEY (game_id, id)
);
CREATE TABLE IF NOT EXISTS comments (
  id TEXT PRIMARY KEY, game_id TEXT NOT NULL, user_id TEXT NOT NULL, user_name TEXT NOT NULL, text TEXT NOT NULL, created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_comments_game ON comments(game_id);
CREATE TABLE IF NOT EXISTS cancellations (game_id TEXT NOT NULL, user_id TEXT NOT NULL, PRIMARY KEY (game_id, user_id));
CREATE TABLE IF NOT EXISTS coupons (id TEXT PRIMARY KEY, user_id TEXT NOT NULL, month_key TEXT NOT NULL, used_game_id TEXT);
CREATE TABLE IF NOT EXISTS issued_months (month_key TEXT PRIMARY KEY);
CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS game_staff (game_id TEXT NOT NULL, user_id TEXT NOT NULL, PRIMARY KEY (game_id, user_id));
CREATE INDEX IF NOT EXISTS idx_game_staff_user ON game_staff(user_id);
CREATE TABLE IF NOT EXISTS notifications (
  id TEXT PRIMARY KEY, user_id TEXT NOT NULL, kind TEXT NOT NULL, game_id TEXT,
  title TEXT NOT NULL, body TEXT NOT NULL, created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications(user_id, created_at);
CREATE TABLE IF NOT EXISTS push_tokens (token TEXT PRIMARY KEY, user_id TEXT NOT NULL, updated_at INTEGER NOT NULL);
CREATE INDEX IF NOT EXISTS idx_push_user ON push_tokens(user_id);
CREATE TABLE IF NOT EXISTS chat_members (user_id TEXT PRIMARY KEY, status TEXT NOT NULL, updated_at INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS chat_messages (
  id TEXT PRIMARY KEY, room TEXT NOT NULL, sender_id TEXT NOT NULL, text TEXT NOT NULL, created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_chat_room ON chat_messages(room, created_at);
CREATE TABLE IF NOT EXISTS chat_reads (user_id TEXT NOT NULL, room TEXT NOT NULL, read_at INTEGER NOT NULL, PRIMARY KEY (user_id, room));
`);

// Profil rasmi (eski bazalarga ustun qo'shiladi)
try { db.exec("ALTER TABLE users ADD COLUMN avatar TEXT NOT NULL DEFAULT ''"); } catch (_) { /* ustun allaqachon bor */ }

function tx(fn) {
  db.exec('BEGIN IMMEDIATE');
  try {
    const r = fn();
    db.exec('COMMIT');
    return r;
  } catch (e) {
    try { db.exec('ROLLBACK'); } catch (_) { /* e'tibor bermaymiz */ }
    throw e;
  }
}

const Q = (sql) => db.prepare(sql);
const now = () => Date.now();
const uid = () => crypto.randomUUID();
const sha256 = (s) => crypto.createHash('sha256').update(s).digest('hex');
const hmac = (v) => crypto.createHmac('sha256', SECRET).update(v).digest('hex');
function safeEqual(a, b) {
  const x = Buffer.from(String(a));
  const y = Buffer.from(String(b));
  return x.length === y.length && crypto.timingSafeEqual(x, y);
}

// ================================================================ Xatolar va javoblar

class ApiError extends Error {
  constructor(status, code, message, extra) {
    super(message);
    this.status = status;
    this.code = code;
    this.extra = extra || {};
  }
}
const fail = (status, code, message, extra) => new ApiError(status, code, message, extra);

function sendJson(res, status, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(body),
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff'
  });
  res.end(body);
}

function readBody(req, limit) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on('data', (c) => {
      size += c.length;
      if (size > limit) { reject(fail(413, 'too_large', "Yuborilgan ma'lumot juda katta.")); req.destroy(); return; }
      chunks.push(c);
    });
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}

async function readJson(req) {
  const buf = await readBody(req, 64 * 1024);
  if (buf.length === 0) return {};
  try { return JSON.parse(buf.toString('utf8')); } catch (_) { throw fail(400, 'bad_json', "So'rov noto'g'ri."); }
}

function clientIp(req) {
  if (TRUST_PROXY) {
    const fwd = String(req.headers['x-forwarded-for'] || '').split(',')[0].trim();
    if (fwd) return fwd;
  }
  return req.socket.remoteAddress || 'unknown';
}

// ================================================================ Telefon, tekshiruvlar

function normalizePhone(input) {
  const digits = String(input || '').replace(/\D/g, '');
  const full = digits.length === 9 ? '998' + digits : digits;
  if (!/^998\d{9}$/.test(full)) return null;
  if (!UZ_MOBILE_PREFIXES.includes(full.slice(3, 5))) return null;
  return full;
}

function fmtPhone(digits) {
  if (!digits || !/^998\d{9}$/.test(digits)) return digits || '';
  return `+998 ${digits.slice(3, 5)} ${digits.slice(5, 8)} ${digits.slice(8, 10)} ${digits.slice(10, 12)}`;
}

function cleanName(v) {
  const n = String(v || '').trim().replace(/\s+/g, ' ');
  if (n.length < 2) throw fail(400, 'invalid_name', 'Ismingizni kiriting (kamida 2 harf).');
  if (n.length > 60) throw fail(400, 'invalid_name', 'Ism juda uzun.');
  return n;
}

function checkLogin(v) {
  const l = String(v || '').trim();
  if (l.length < 4) throw fail(400, 'invalid_login', "Logon kamida 4 ta belgidan iborat bo'lsin.");
  if (l.length > 20) throw fail(400, 'invalid_login', 'Logon 20 ta belgidan oshmasin.');
  if (!/^[A-Za-z0-9_.]+$/.test(l)) throw fail(400, 'invalid_login', "Logonda faqat lotin harflari, raqam, _ va . bo'lishi mumkin.");
  return l;
}

function checkPassword(p, login) {
  const s = String(p || '');
  if (s.length < 6) throw fail(400, 'invalid_password', "Parol kamida 6 ta belgidan iborat bo'lsin.");
  if (s.length > 64) throw fail(400, 'invalid_password', 'Parol juda uzun.');
  if (login && s.toLowerCase() === String(login).toLowerCase()) throw fail(400, 'invalid_password', "Parol logon bilan bir xil bo'lmasin.");
  return s;
}

const hashPassword = (password, salt) => crypto.scryptSync(String(password), salt, 64).toString('hex');

// ================================================================ Foydalanuvchilar va sessiyalar

function userJson(u) {
  return { id: u.id, name: u.name, phone: fmtPhone(u.phone), login: u.login, role: u.role, joinedAt: u.joined_at, avatar: u.avatar || '' };
}

function usingDefaultPassword(u) {
  if (!u || u.role !== 'ADMIN') return false;
  const seed = SEED_ADMINS.find((a) => a.login.toLowerCase() === String(u.login).toLowerCase());
  return !!seed && safeEqual(u.pass_hash, hashPassword(seed.password, u.salt));
}

function createSession(userId) {
  const token = crypto.randomBytes(32).toString('hex');
  const t = now();
  Q('INSERT INTO sessions (token_hash, user_id, created_at, expires_at) VALUES (?,?,?,?)').run(sha256(token), userId, t, t + SESSION_TTL_MS);
  return token;
}

function authUser(req) {
  const m = /^Bearer ([a-f0-9]{64})$/.exec(String(req.headers['authorization'] || ''));
  if (!m) return null;
  return Q('SELECT u.* FROM sessions s JOIN users u ON u.id = s.user_id WHERE s.token_hash = ? AND s.expires_at > ?').get(sha256(m[1]), now()) || null;
}

function requireUser(ctx) {
  if (!ctx.user) throw fail(401, 'unauthorized', 'Sessiya tugagan. Qayta kiring.');
  return ctx.user;
}

function requireAdmin(ctx) {
  const u = requireUser(ctx);
  if (u.role !== 'ADMIN') throw fail(403, 'forbidden', 'Bu amal faqat admin uchun.');
  return u;
}

/** Bosh admin yoki ishchi admin (o'yin boshqaruvining cheklangan qismi uchun). */
function requireManager(ctx) {
  const u = requireUser(ctx);
  if (u.role !== 'ADMIN' && u.role !== 'STAFF') throw fail(403, 'forbidden', 'Bu amal faqat adminlar uchun.');
  return u;
}

function insertStaffLike(role, name, login, password) {
  const salt = crypto.randomBytes(16).toString('hex');
  const id = uid();
  Q('INSERT INTO users (id, name, phone, login, pass_hash, salt, role, joined_at) VALUES (?,?,NULL,?,?,?,?,?)')
    .run(id, name, login, hashPassword(password, salt), salt, role, now());
  return id;
}

function seedAdmins() {
  for (const a of SEED_ADMINS) {
    const existing = Q('SELECT * FROM users WHERE login = ? COLLATE NOCASE').get(a.login);
    if (existing) {
      if (existing.role !== 'ADMIN') throw new Error('Bootstrap login is already used by a non-admin account.');
      continue;
    }
    insertStaffLike('ADMIN', a.name, a.login, a.password);
    console.log('Bootstrap administrator created.');
  }
}

// ================================================================ SMS (Eskiz) va OTP

let eskizToken = null;
let eskizTokenAt = 0;

async function eskizLogin() {
  const form = new URLSearchParams({ email: ESKIZ_EMAIL, password: ESKIZ_PASSWORD });
  const r = await fetch('https://notify.eskiz.uz/api/auth/login', { method: 'POST', body: form });
  const j = await r.json().catch(() => ({}));
  const token = j && j.data && j.data.token;
  if (!r.ok || !token) throw new Error('Eskiz login xatosi: ' + (j && j.message ? j.message : r.status));
  eskizToken = token;
  eskizTokenAt = now();
}

async function sendSms(phone, text) {
  if (DEV_MODE) { console.log(`[DEV] SMS -> ${phone}: ${text}`); return; }
  if (!ESKIZ_EMAIL || !ESKIZ_PASSWORD) throw new Error('ESKIZ_EMAIL / ESKIZ_PASSWORD berilmagan');
  if (!eskizToken || now() - eskizTokenAt > 20 * 24 * 3600 * 1000) await eskizLogin();
  const attempt = () => fetch('https://notify.eskiz.uz/api/message/sms/send', {
    method: 'POST',
    headers: { Authorization: 'Bearer ' + eskizToken },
    body: new URLSearchParams({ mobile_phone: phone, message: text, from: ESKIZ_FROM })
  });
  let r = await attempt();
  if (r.status === 401) { await eskizLogin(); r = await attempt(); }
  const j = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error('Eskiz SMS xatosi: ' + (j && j.message ? JSON.stringify(j.message) : r.status));
}

/** Telegram Gateway orqali kod yuboradi. Kodni biz yaratamiz, tekshirish ham serverda (otpVerify) qoladi. */
async function sendTelegramCode(phone, code) {
  let r;
  try {
    r = await fetch('https://gatewayapi.telegram.org/sendVerificationMessage', {
      method: 'POST',
      headers: { Authorization: 'Bearer ' + TELEGRAM_GATEWAY_TOKEN, 'Content-Type': 'application/json' },
      body: JSON.stringify({ phone_number: '+' + phone, code, ttl: 120 }),
      signal: AbortSignal.timeout(10000)
    });
  } catch (e) {
    throw new Error('Telegram Gateway bilan aloqa bo\'lmadi: ' + e.message);
  }
  const j = await r.json().catch(() => ({}));
  if (r.ok && j && j.ok) return;
  const err = String((j && j.error) || r.status);
  const e = new Error('Telegram Gateway xatosi: ' + err);
  // Raqam Telegram'da ro'yxatdan o'tmagan bo'lsa, foydalanuvchiga aniq xabar beramiz
  if (/PHONE_NUMBER_NOT_FOUND/i.test(err)) e.telegramNotFound = true;
  throw e;
}

/** Kodni yetkazish: sinov rejimida konsolga, token bo'lsa Telegram orqali, aks holda Eskiz SMS. */
/** Bot orqali kod yuboradi. Foydalanuvchi botga raqamini ulashmagan bo'lsa, needBot belgisi bilan xato tashlaydi. */
async function sendBotCode(phone, code) {
  const row = Q('SELECT tg_id FROM tg_phones WHERE phone = ? ORDER BY rowid DESC LIMIT 1').get(phone);
  if (!row) { const e = new Error('bot_not_linked'); e.needBot = true; throw e; }
  let r;
  try { r = await tgApi('sendMessage', { chat_id: row.tg_id, text: `🔐 Treble tasdiqlash kodi: ${code}\n\nKodni hech kimga bermang.` }); }
  catch (e) { throw new Error('Telegram bot bilan aloqa bo\'lmadi: ' + e.message); }
  if (!r || !r.ok) {
    // Foydalanuvchi botni to'xtatgan/bloklagan bo'lishi mumkin: qaytadan botga kirishi kerak
    const e = new Error('bot_send_failed: ' + (r && r.description)); e.needBot = true; throw e;
  }
}

function botLink() { return BOT_USERNAME ? `https://t.me/${BOT_USERNAME}?start=otp` : ''; }

async function deliverCode(phone, code) {
  if (DEV_MODE) return sendSms(phone, SMS_TEMPLATE.replace('{code}', code));
  if (OTP_VIA_BOT) return sendBotCode(phone, code);
  if (TELEGRAM_GATEWAY_TOKEN) return sendTelegramCode(phone, code);
  return sendSms(phone, SMS_TEMPLATE.replace('{code}', code));
}

const otps = new Map();        // "purpose:phone" -> { hash, expiresAt, attempts, lastSentAt }
const phoneSends = new Map();
const ipSends = new Map();
const usedTokens = new Map();  // token -> exp

function recent(map, key, windowMs) {
  const t = now();
  const list = (map.get(key) || []).filter((x) => t - x < windowMs);
  map.set(key, list);
  return list;
}

function makeToken(phone, purpose) {
  const body = `${phone}.${purpose}.${now() + TOKEN_TTL_MS}`;
  return `${body}.${hmac('token:' + body)}`;
}

/** Tokenni tekshiradi va bir marta ishlatilgan deb belgilaydi. */
function consumeToken(token, phone, purpose) {
  const parts = String(token || '').split('.');
  if (parts.length !== 4) return false;
  const [p, pur, exp, sig] = parts;
  if (p !== phone || pur !== purpose) return false;
  if (!safeEqual(sig, hmac(`token:${p}.${pur}.${exp}`))) return false;
  if (now() > Number(exp)) return false;
  if (usedTokens.has(token)) return false;
  usedTokens.set(token, Number(exp));
  return true;
}

setInterval(() => {
  const t = now();
  for (const [k, rec] of otps) if (rec.expiresAt + 600000 < t) otps.delete(k);
  for (const [k, exp] of usedTokens) if (exp < t) usedTokens.delete(k);
  for (const map of [phoneSends, ipSends]) {
    for (const [k, list] of map) {
      const fresh = list.filter((x) => t - x < 3600000);
      if (fresh.length === 0) map.delete(k); else map.set(k, fresh);
    }
  }
  Q('DELETE FROM sessions WHERE expires_at < ?').run(t);
}, 5 * 60 * 1000).unref();

async function otpSend(ctx) {
  const b = ctx.body;
  const purpose = b.purpose === 'reset' ? 'reset' : 'register';
  const phone = normalizePhone(b.phone);
  if (!phone) throw fail(400, 'invalid_phone', "Telefon raqami noto'g'ri yoki O'zbekiston raqami emas.");

  const existing = Q('SELECT id FROM users WHERE phone = ?').get(phone);
  if (purpose === 'register' && existing) throw fail(409, 'phone_taken', "Bu telefon raqami bilan allaqachon ro'yxatdan o'tilgan.");
  if (purpose === 'reset' && !existing) throw fail(404, 'no_account', 'Bu raqam bilan hisob topilmadi.');
  if (purpose === 'register' && !OTP_REQUIRED) return { status: 200, body: { ok: true, skipped: true } };

  const key = `${purpose}:${phone}`;
  const t = now();
  const rec = otps.get(key);
  if (rec && t - rec.lastSentAt < RESEND_COOLDOWN_MS) {
    const retryAfter = Math.ceil((RESEND_COOLDOWN_MS - (t - rec.lastSentAt)) / 1000);
    throw fail(429, 'too_many_requests', `Yangi kod so'rash uchun ${retryAfter} soniya kuting.`, { retryAfter });
  }
  if (recent(phoneSends, phone, 3600000).length >= MAX_SENDS_PER_HOUR_PHONE || recent(ipSends, ctx.ip, 3600000).length >= MAX_SENDS_PER_HOUR_IP) {
    throw fail(429, 'hourly_limit', "Juda ko'p urinish bo'ldi. Birozdan keyin qayta urinib ko'ring.", { retryAfter: 3600 });
  }

  const code = String(crypto.randomInt(100000, 1000000));
  try {
    await deliverCode(phone, code);
  } catch (e) {
    if (e.needBot) {
      // Kod hali yuborilmadi: foydalanuvchi botga o'tib raqamini ulashganda bot kodni yuboradi
      otps.set(key, { hash: hmac(`code:${key}:${code}`), expiresAt: t + BOT_PENDING_TTL_MS, attempts: 0, lastSentAt: t, pendingCode: code });
      recent(phoneSends, phone, 3600000).push(t);
      recent(ipSends, ctx.ip, 3600000).push(t);
      return { status: 200, body: { ok: true, needBot: true, botUrl: botLink(), retryAfter: Math.floor(RESEND_COOLDOWN_MS / 1000), expiresIn: Math.floor(BOT_PENDING_TTL_MS / 1000) } };
    }
    console.error('[Kod yuborish xatosi]', e.message);
    if (e.telegramNotFound) {
      throw fail(400, 'telegram_not_found', "Bu telefon raqami Telegram'da ro'yxatdan o'tmagan. Raqamingiz bilan Telegram'ga kiring va qayta urinib ko'ring.");
    }
    throw fail(502, 'sms_failed', "Kod yuborib bo'lmadi. Birozdan keyin qayta urinib ko'ring.");
  }
  otps.set(key, { hash: hmac(`code:${key}:${code}`), expiresAt: t + CODE_TTL_MS, attempts: 0, lastSentAt: t });
  recent(phoneSends, phone, 3600000).push(t);
  recent(ipSends, ctx.ip, 3600000).push(t);
  return { status: 200, body: { ok: true, retryAfter: Math.floor(RESEND_COOLDOWN_MS / 1000), expiresIn: Math.floor(CODE_TTL_MS / 1000) } };
}

function otpVerify(ctx) {
  const b = ctx.body;
  const purpose = b.purpose === 'reset' ? 'reset' : 'register';
  const phone = normalizePhone(b.phone);
  const code = String(b.code || '').trim();
  if (!phone || !/^\d{6}$/.test(code)) throw fail(400, 'invalid_request', "So'rov noto'g'ri.");
  const key = `${purpose}:${phone}`;
  const rec = otps.get(key);
  if (!rec) throw fail(410, 'no_code', "Kod muddati tugagan. Yangi kod so'rang.");
  if (now() > rec.expiresAt) { otps.delete(key); throw fail(410, 'expired', "Kod muddati tugagan. Yangi kod so'rang."); }
  if (rec.attempts >= MAX_ATTEMPTS) { otps.delete(key); throw fail(429, 'too_many_attempts', "Juda ko'p xato urinish. Yangi kod so'rang."); }
  rec.attempts += 1;
  if (!safeEqual(rec.hash, hmac(`code:${key}:${code}`))) {
    const left = Math.max(0, MAX_ATTEMPTS - rec.attempts);
    throw fail(400, 'wrong_code', `Kod noto'g'ri. Qolgan urinishlar: ${left}.`, { attemptsLeft: left });
  }
  otps.delete(key);
  return { status: 200, body: { ok: true, token: makeToken(phone, purpose) } };
}

// ================================================================ Hisob (auth)

const loginFails = new Map(); // "ip|login" -> [timestamps]

function authBody(user) {
  return { token: createSession(user.id), user: userJson(user), usingDefaultPassword: usingDefaultPassword(user) };
}

function register(ctx) {
  const b = ctx.body;
  const name = cleanName(b.name);
  const phone = normalizePhone(b.phone);
  if (!phone) throw fail(400, 'invalid_phone', "Telefon raqami noto'g'ri. 9 ta raqam kiriting, masalan: 90 123 45 67");
  const login = checkLogin(b.login);
  const password = checkPassword(b.password, login);

  if (Q('SELECT 1 FROM users WHERE login = ?').get(login)) throw fail(409, 'login_taken', 'Bu logon band. Boshqasini tanlang.');
  if (Q('SELECT 1 FROM users WHERE phone = ?').get(phone)) throw fail(409, 'phone_taken', "Bu telefon raqami bilan allaqachon ro'yxatdan o'tilgan.");
  if (OTP_REQUIRED && !consumeToken(b.otpToken, phone, 'register')) {
    throw fail(400, 'otp_invalid', 'Telefon tasdiqlanmagan yoki tasdiqlash muddati tugagan. SMS kodni qayta kiriting.');
  }

  const salt = crypto.randomBytes(16).toString('hex');
  const id = uid();
  Q("INSERT INTO users (id, name, phone, login, pass_hash, salt, role, joined_at) VALUES (?,?,?,?,?,?, 'USER', ?)")
    .run(id, name, phone, login, hashPassword(password, salt), salt, now());
  return { status: 200, body: authBody(Q('SELECT * FROM users WHERE id = ?').get(id)) };
}

function login(ctx) {
  const l = String(ctx.body.login || '').trim();
  const p = String(ctx.body.password || '');
  if (!l || !p) throw fail(400, 'invalid_request', 'Logon va parolni kiriting.');
  const key = `${ctx.ip}|${l.toLowerCase()}`;
  if (recent(loginFails, key, 15 * 60 * 1000).length >= 10) {
    throw fail(429, 'locked', "Juda ko'p xato urinish. 15 daqiqadan keyin qayta urinib ko'ring.");
  }
  const u = Q('SELECT * FROM users WHERE login = ?').get(l);
  // Foydalanuvchi bo'lmasa ham hash hisoblanadi: javob vaqti bir xil bo'lishi uchun
  const calc = hashPassword(p, u ? u.salt : 'x'.repeat(32));
  if (!u || !safeEqual(u.pass_hash, calc)) {
    recent(loginFails, key, 15 * 60 * 1000).push(now());
    throw fail(401, 'bad_credentials', "Logon yoki parol noto'g'ri.");
  }
  loginFails.delete(key);
  return { status: 200, body: authBody(u) };
}

function resetPassword(ctx) {
  const b = ctx.body;
  const phone = normalizePhone(b.phone);
  if (!phone) throw fail(400, 'invalid_phone', "Telefon raqami noto'g'ri.");
  const u = Q('SELECT * FROM users WHERE phone = ?').get(phone);
  if (!u) throw fail(404, 'no_account', 'Bu raqam bilan hisob topilmadi.');
  const password = checkPassword(b.newPassword, u.login);
  if (!consumeToken(b.otpToken, phone, 'reset')) {
    throw fail(400, 'otp_invalid', 'Telefon tasdiqlanmagan yoki tasdiqlash muddati tugagan. SMS kodni qayta kiriting.');
  }
  const salt = crypto.randomBytes(16).toString('hex');
  Q('UPDATE users SET pass_hash = ?, salt = ? WHERE id = ?').run(hashPassword(password, salt), salt, u.id);
  Q('DELETE FROM sessions WHERE user_id = ?').run(u.id);
  return { status: 200, body: authBody(Q('SELECT * FROM users WHERE id = ?').get(u.id)) };
}

function logout(ctx) {
  const m = /^Bearer ([a-f0-9]{64})$/.exec(String(ctx.req.headers['authorization'] || ''));
  if (m) Q('DELETE FROM sessions WHERE token_hash = ?').run(sha256(m[1]));
  return { status: 200, body: { ok: true } };
}

function getMe(ctx) {
  const u = requireUser(ctx);
  return { status: 200, body: { user: userJson(u), usingDefaultPassword: usingDefaultPassword(u) } };
}

function updateMe(ctx) {
  const u = requireUser(ctx);
  const name = cleanName(ctx.body.name);
  tx(() => {
    Q('UPDATE users SET name = ? WHERE id = ?').run(name, u.id);
    Q('UPDATE players SET name = ? WHERE id = ?').run(name, u.id);
    Q('UPDATE comments SET user_name = ? WHERE user_id = ?').run(name, u.id);
  });
  return { status: 200, body: { user: userJson(Q('SELECT * FROM users WHERE id = ?').get(u.id)) } };
}

function changePassword(ctx) {
  const u = requireUser(ctx);
  if (!safeEqual(u.pass_hash, hashPassword(ctx.body.oldPassword || '', u.salt))) throw fail(400, 'bad_password', "Joriy parol noto'g'ri.");
  const np = checkPassword(ctx.body.newPassword, u.login);
  if (np === String(ctx.body.oldPassword)) throw fail(400, 'same_password', 'Yangi parol eskisidan farq qilishi kerak.');
  const salt = crypto.randomBytes(16).toString('hex');
  Q('UPDATE users SET pass_hash = ?, salt = ? WHERE id = ?').run(hashPassword(np, salt), salt, u.id);
  return { status: 200, body: { ok: true, usingDefaultPassword: false } };
}

function deleteMe(ctx) {
  const u = requireUser(ctx);
  if (u.role !== 'USER') throw fail(400, 'admin_protected', "Admin hisobini o'chirib bo'lmaydi.");
  if (!safeEqual(u.pass_hash, hashPassword(ctx.body.password || '', u.salt))) throw fail(400, 'bad_password', "Parol noto'g'ri.");
  tx(() => {
    for (const g of Q('SELECT g.* FROM games g JOIN players p ON p.game_id = g.id WHERE p.id = ?').all(u.id)) {
      if (!isOver(g)) Q('DELETE FROM players WHERE game_id = ? AND id = ?').run(g.id, u.id);
    }
    Q('DELETE FROM cancellations WHERE user_id = ?').run(u.id);
    Q('DELETE FROM coupons WHERE user_id = ?').run(u.id);
    Q('DELETE FROM sessions WHERE user_id = ?').run(u.id);
    Q('DELETE FROM notifications WHERE user_id = ?').run(u.id);
    deleteChatData(u.id);
    Q('DELETE FROM users WHERE id = ?').run(u.id);
  });
  removeUploadFile(u.avatar);
  return { status: 200, body: { ok: true } };
}

// ================================================================ O'yinlar

function parseMinutes(hhmm) {
  const m = /^([01]?\d|2[0-3]):([0-5]\d)$/.exec(String(hhmm || '').trim());
  return m ? Number(m[1]) * 60 + Number(m[2]) : null;
}
const startMillisOf = (g) => g.date_millis + (parseMinutes(g.start_time) || 0) * 60000;
const endMillisOf = (g) => startMillisOf(g) + g.duration_min * 60000;
const isOver = (g, t = now()) => !!g.is_finished || t > endMillisOf(g);
const monthKeyOf = (ms) => {
  const d = new Date(ms + TZ_OFFSET_MS);
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`;
};

function gamesToJson(viewer, onlyId) {
  const isManager = !!viewer && (viewer.role === 'ADMIN' || viewer.role === 'STAFF');
  const hidePay = !!viewer && viewer.role === 'STAFF'; // ishchi admin faqat o'ziga biriktirilgan o'yinlarni ko'radi
  let gRows = onlyId ? Q('SELECT * FROM games WHERE id = ?').all(onlyId) : Q('SELECT * FROM games ORDER BY created_at DESC').all();
  if (hidePay) {
    // Ishchi admin faqat o'ziga biriktirilgan o'yinlarni ko'radi
    const mine = new Set(Q('SELECT game_id FROM game_staff WHERE user_id = ?').all(viewer.id).map((r) => r.game_id));
    gRows = gRows.filter((g) => mine.has(g.id));
  }
  const staffRows = isManager ? Q('SELECT gs.game_id, u.id, u.name FROM game_staff gs JOIN users u ON u.id = gs.user_id ORDER BY u.name COLLATE NOCASE').all() : [];
  const staffByGame = new Map();
  for (const r of staffRows) { if (!staffByGame.has(r.game_id)) staffByGame.set(r.game_id, []); staffByGame.get(r.game_id).push({ id: r.id, name: r.name }); }
  const avatars = new Map(Q("SELECT id, avatar FROM users WHERE avatar != ''").all().map((r) => [r.id, r.avatar]));
  const pRows = onlyId ? Q('SELECT * FROM players WHERE game_id = ? ORDER BY booked_at').all(onlyId) : Q('SELECT * FROM players ORDER BY booked_at').all();
  const cRows = onlyId ? Q('SELECT * FROM comments WHERE game_id = ? ORDER BY created_at').all(onlyId) : Q('SELECT * FROM comments ORDER BY created_at').all();
  const xRows = viewer
    ? (onlyId ? Q('SELECT game_id FROM cancellations WHERE game_id = ? AND user_id = ?').all(onlyId, viewer.id)
      : Q('SELECT game_id FROM cancellations WHERE user_id = ?').all(viewer.id))
    : [];
  const group = (rows, key) => {
    const m = new Map();
    for (const r of rows) { if (!m.has(r[key])) m.set(r[key], []); m.get(r[key]).push(r); }
    return m;
  };
  const players = group(pRows, 'game_id');
  const comments = group(cRows, 'game_id');
  const cancelled = new Set(xRows.map((r) => r.game_id));

  return gRows.map((g) => ({
    id: g.id,
    venueName: g.venue_name,
    address: g.address,
    mapLink: g.map_link,
    images: JSON.parse(g.images || '[]'),
    dateMillis: g.date_millis,
    startTime: g.start_time,
    durationMin: g.duration_min,
    format: g.format,
    maxPlayers: g.max_players,
    price: g.price,
    description: g.description,
    rules: g.rules,
    payInfo: g.pay_info,
    freeCancelHours: g.free_cancel_hours,
    players: (players.get(g.id) || []).map((p) => {
      const o = {
        id: p.id,
        name: p.name,
        phone: isManager || (viewer && viewer.id === p.id) ? fmtPhone(p.phone) : '',
        paid: !!p.paid,
        payMethod: p.pay_method,
        bookedAt: p.booked_at,
        avatar: avatars.get(p.id) || ''
      };
      if (p.attended !== null && p.attended !== undefined) o.attended = !!p.attended;
      return o;
    }),
    comments: (comments.get(g.id) || []).map((c) => ({ id: c.id, userId: c.user_id, userName: c.user_name, text: c.text, createdAt: c.created_at, avatar: avatars.get(c.user_id) || '' })),
    staff: staffByGame.get(g.id) || [],
    cancelledBy: viewer && cancelled.has(g.id) ? [viewer.id] : [],
    isFinished: !!g.is_finished,
    isCancelled: !!g.is_cancelled,
    createdAt: g.created_at
  }));
}

const gameBody = (viewer, id) => ({ status: 200, body: { game: gamesToJson(viewer, id)[0] } });

function parseGameInput(b) {
  const str = (v, max, label, required) => {
    const s = String(v === undefined || v === null ? '' : v).trim();
    if (required && !s) throw fail(400, 'invalid_game', `${label}ni kiriting.`);
    if (s.length > max) throw fail(400, 'invalid_game', `${label} juda uzun.`);
    return s;
  };
  const int = (v, min, max, label) => {
    const n = Number(v);
    if (!Number.isInteger(n) || n < min || n > max) throw fail(400, 'invalid_game', `${label} ${min} dan ${max} gacha bo'lsin.`);
    return n;
  };
  const venueName = str(b.venueName, 80, 'Maydon nomi', true);
  const minutes = parseMinutes(b.startTime);
  if (minutes === null) throw fail(400, 'invalid_game', "Boshlanish vaqtini SS:DD ko'rinishida kiriting.");
  const startTime = `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`;
  const mapLink = str(b.mapLink, 300, 'Xarita havolasi', false);
  if (mapLink && !/^(https?:\/\/|www\.|maps\.)/i.test(mapLink)) throw fail(400, 'invalid_game', "Xarita havolasi noto'g'ri.");
  const images = Array.isArray(b.images) ? b.images.map((x) => String(x)) : [];
  if (images.length > 8) throw fail(400, 'invalid_game', "Ko'pi bilan 8 ta rasm mumkin.");
  for (const im of images) {
    if (im.length > 300 || !(im.startsWith('/uploads/') || /^https?:\/\//i.test(im))) throw fail(400, 'invalid_game', "Rasm havolasi noto'g'ri.");
  }
  return {
    venue_name: venueName,
    address: str(b.address, 160, 'Manzil', false),
    map_link: mapLink,
    images: JSON.stringify(images),
    date_millis: int(b.dateMillis, 1, 8.64e15, 'Sana'),
    start_time: startTime,
    duration_min: int(b.durationMin, 15, 480, 'Davomiylik'),
    format: str(b.format, 10, 'Format', true),
    max_players: int(b.maxPlayers, 2, 40, "Maksimal o'yinchi soni"),
    price: int(b.price, 0, 10000000, 'Narx'),
    description: str(b.description, 600, "O'yin tavsifi", false),
    rules: str(b.rules, 600, 'Qoidalar', false),
    pay_info: str(b.payInfo, 120, "To'lov ma'lumoti", false),
    free_cancel_hours: int(b.freeCancelHours === undefined ? 24 : b.freeCancelHours, 0, 168, 'Bekor qilish muddati'),
    imagesList: images,
    // null = o'zgartirilmasin (eski ilova versiyasi), massiv = shu ishchi adminlar biriktirilsin
    staffIds: Array.isArray(b.staffIds) ? [...new Set(b.staffIds.map((x) => String(x)))].slice(0, 20) : null
  };
}

/** Faqat haqiqatan mavjud ishchi adminlarni qoldiradi va o'yinga biriktiradi. Yangi biriktirilganlarga xabar yuboriladi. */
function assignStaff(gameId, staffIds) {
  if (staffIds === null) return;
  const before = new Set(Q('SELECT user_id FROM game_staff WHERE game_id = ?').all(gameId).map((r) => r.user_id));
  Q('DELETE FROM game_staff WHERE game_id = ?').run(gameId);
  const added = [];
  for (const id of staffIds) {
    if (Q("SELECT 1 FROM users WHERE id = ? AND role = 'STAFF'").get(id)) {
      Q('INSERT OR IGNORE INTO game_staff (game_id, user_id) VALUES (?,?)').run(gameId, id);
      if (!before.has(id)) added.push(id);
    }
  }
  if (added.length) {
    const g = Q('SELECT * FROM games WHERE id = ?').get(gameId);
    if (g) {
      for (const id of added) {
        const nid = uid();
        const title = "🛠 Sizga o'yin biriktirildi";
        const body = `${g.venue_name} · ${gameWhenText(g)}`;
        Q('INSERT INTO notifications (id, user_id, kind, game_id, title, body, created_at) VALUES (?,?,?,?,?,?,?)')
          .run(nid, id, 'ASSIGNED', gameId, title, body, now());
        pushToUsers([id], { type: 'notif', id: 'srv:' + nid, kind: 'ASSIGNED', gameId, title, body, tab: 0 });
      }
    }
  }
}

/** Ishchi admin faqat o'ziga biriktirilgan o'yinda ishlay oladi. */
function requireAssigned(u, gameId) {
  if (u.role !== 'STAFF') return;
  if (!Q('SELECT 1 FROM game_staff WHERE game_id = ? AND user_id = ?').get(gameId, u.id)) {
    throw fail(403, 'not_assigned', "Bu o'yin sizga biriktirilmagan.");
  }
}

function removeOrphanFiles(candidates) {
  for (const im of candidates) {
    if (!im.startsWith('/uploads/')) continue;
    const used = Q('SELECT 1 FROM games WHERE images LIKE ? LIMIT 1').get('%' + im + '%');
    if (!used) {
      try { fs.unlinkSync(path.join(UPLOAD_DIR, path.basename(im))); } catch (_) { /* allaqachon yo'q */ }
    }
  }
}

function getGames(ctx) {
  const u = requireUser(ctx);
  return { status: 200, body: { games: gamesToJson(u), serverTime: now() } };
}

function createGame(ctx) {
  const u = requireAdmin(ctx);
  const g = parseGameInput(ctx.body);
  const id = uid();
  Q(`INSERT INTO games (id, venue_name, address, map_link, images, date_millis, start_time, duration_min, format, max_players, price,
      description, rules, pay_info, free_cancel_hours, is_finished, is_cancelled, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,0,?)`)
    .run(id, g.venue_name, g.address, g.map_link, g.images, g.date_millis, g.start_time, g.duration_min, g.format, g.max_players, g.price,
      g.description, g.rules, g.pay_info, g.free_cancel_hours, now());
  assignStaff(id, g.staffIds);
  return gameBody(u, id);
}

function getGameRow(id) {
  const g = Q('SELECT * FROM games WHERE id = ?').get(id);
  if (!g) throw fail(404, 'not_found', "O'yin topilmadi.");
  return g;
}

function editGame(ctx) {
  const u = requireAdmin(ctx);
  const old = getGameRow(ctx.params.id);
  const g = parseGameInput(ctx.body);
  const count = Q('SELECT COUNT(*) AS c FROM players WHERE game_id = ?').get(old.id).c;
  if (g.max_players < count) throw fail(400, 'invalid_game', `Maksimal o'yinchi soni band qilganlar sonidan (${count}) kam bo'lmasin.`);
  Q(`UPDATE games SET venue_name=?, address=?, map_link=?, images=?, date_millis=?, start_time=?, duration_min=?, format=?, max_players=?,
      price=?, description=?, rules=?, pay_info=?, free_cancel_hours=? WHERE id=?`)
    .run(g.venue_name, g.address, g.map_link, g.images, g.date_millis, g.start_time, g.duration_min, g.format, g.max_players, g.price,
      g.description, g.rules, g.pay_info, g.free_cancel_hours, old.id);
  assignStaff(old.id, g.staffIds);
  if (old.date_millis !== g.date_millis || old.start_time !== g.start_time) {
    const fresh = Q('SELECT * FROM games WHERE id = ?').get(old.id);
    notifyGamePlayers(fresh, 'CHANGED', "🕒 O'yin vaqti o'zgardi", `${fresh.venue_name} · yangi vaqt: ${gameWhenText(fresh)}`);
  }
  const removed = JSON.parse(old.images || '[]').filter((x) => !g.imagesList.includes(x));
  removeOrphanFiles(removed);
  return gameBody(u, old.id);
}

function deleteGame(ctx) {
  requireAdmin(ctx);
  const g = getGameRow(ctx.params.id);
  tx(() => {
    Q('UPDATE coupons SET used_game_id = NULL WHERE used_game_id = ?').run(g.id);
    Q('DELETE FROM players WHERE game_id = ?').run(g.id);
    Q('DELETE FROM comments WHERE game_id = ?').run(g.id);
    Q('DELETE FROM cancellations WHERE game_id = ?').run(g.id);
    Q('DELETE FROM game_staff WHERE game_id = ?').run(g.id);
    Q('DELETE FROM games WHERE id = ?').run(g.id);
  });
  removeOrphanFiles(JSON.parse(g.images || '[]'));
  return { status: 200, body: { ok: true } };
}

function setFlag(column) {
  return (ctx) => {
    // Bekor qilish faqat bosh admin; "yakunlandi" belgisini ishchi admin ham qo'ya oladi
    const u = column === 'is_cancelled' ? requireAdmin(ctx) : requireManager(ctx);
    const g = getGameRow(ctx.params.id);
    requireAssigned(u, g.id);
    const key = column === 'is_cancelled' ? 'cancelled' : 'finished';
    Q(`UPDATE games SET ${column} = ? WHERE id = ?`).run(ctx.body[key] ? 1 : 0, g.id);
    if (column === 'is_cancelled' && ctx.body[key] && !g.is_cancelled) {
      notifyGamePlayers(g, 'CANCELLED', "❌ O'yin bekor qilindi", `${g.venue_name} · ${gameWhenText(g)}`);
    }
    return gameBody(u, g.id);
  };
}

function adminPlayerAction(fn, opts = {}) {
  return (ctx) => {
    const u = opts.staffAllowed ? requireManager(ctx) : requireAdmin(ctx);
    const g = getGameRow(ctx.params.id);
    requireAssigned(u, g.id);
    const p = Q('SELECT * FROM players WHERE game_id = ? AND id = ?').get(g.id, ctx.params.pid);
    if (!p) throw fail(404, 'not_found', "O'yinchi topilmadi.");
    fn(g, p, ctx.body);
    return gameBody(u, g.id);
  };
}

function addGuest(ctx) {
  const u = requireAdmin(ctx);
  const g = getGameRow(ctx.params.id);
  const name = String(ctx.body.name || '').trim();
  if (name.length < 2) throw fail(400, 'invalid_name', 'Ismni kiriting (kamida 2 harf).');
  if (name.length > 40) throw fail(400, 'invalid_name', 'Ism juda uzun.');
  if (g.is_cancelled) throw fail(400, 'cancelled', "O'yin bekor qilingan.");
  const count = Q('SELECT COUNT(*) AS c FROM players WHERE game_id = ?').get(g.id).c;
  if (count >= g.max_players) throw fail(400, 'full', "O'yinda joy qolmadi.");
  Q('INSERT INTO players (game_id, id, name, phone, attended, paid, pay_method, booked_at) VALUES (?,?,?,?,NULL,?,?,?)')
    .run(g.id, 'guest-' + uid(), name, String(ctx.body.phone || '').trim().slice(0, 20), g.price === 0 ? 1 : 0, 'CASH', now());
  return gameBody(u, g.id);
}

function book(ctx) {
  const u = requireUser(ctx);
  if (u.role !== 'USER') throw fail(400, 'admin_cannot_book', "Adminlar o'yinga band qila olmaydi.");
  const method = ['CASH', 'CARD', 'COUPON'].includes(ctx.body.method) ? ctx.body.method : 'CASH';
  tx(() => {
    const g = getGameRow(ctx.params.id);
    if (g.is_cancelled) throw fail(400, 'cancelled', "Bu o'yin bekor qilingan.");
    if (isOver(g)) throw fail(400, 'finished', "Bu o'yin yakunlangan.");
    const count = Q('SELECT COUNT(*) AS c FROM players WHERE game_id = ?').get(g.id).c;
    if (count >= g.max_players) throw fail(400, 'full', "Afsuski, o'yinda joy qolmadi.");
    if (Q('SELECT 1 FROM players WHERE game_id = ? AND id = ?').get(g.id, u.id)) throw fail(400, 'already', 'Siz allaqachon band qilgansiz.');

    const payMethod = g.price === 0 ? 'CASH' : method;
    let paid = g.price === 0 ? 1 : 0;
    if (payMethod === 'COUPON') {
      const c = Q('SELECT id FROM coupons WHERE user_id = ? AND used_game_id IS NULL ORDER BY month_key LIMIT 1').get(u.id);
      if (!c) throw fail(400, 'no_coupon', "Sizda foydalanilmagan kupon yo'q.");
      Q('UPDATE coupons SET used_game_id = ? WHERE id = ?').run(g.id, c.id);
      paid = 1;
    }
    Q('INSERT INTO players (game_id, id, name, phone, attended, paid, pay_method, booked_at) VALUES (?,?,?,?,NULL,?,?,?)')
      .run(g.id, u.id, u.name, u.phone || '', paid, payMethod, now());
    Q('DELETE FROM cancellations WHERE game_id = ? AND user_id = ?').run(g.id, u.id);
  });
  return gameBody(u, ctx.params.id);
}

function cancelBooking(ctx) {
  const u = requireUser(ctx);
  tx(() => {
    const g = getGameRow(ctx.params.id);
    if (!Q('SELECT 1 FROM players WHERE game_id = ? AND id = ?').get(g.id, u.id)) throw fail(400, 'not_joined', "Siz bu o'yinga band qilmagansiz.");
    if (isOver(g)) throw fail(400, 'finished', "O'yin yakunlangan.");
    const msLeft = startMillisOf(g) - now();
    if (!g.is_cancelled && msLeft < g.free_cancel_hours * 3600000) {
      throw fail(400, 'too_late', `O'yin boshlanishiga ${g.free_cancel_hours} soatdan kam qoldi. Bandni bekor qilib bo'lmaydi, tashkilotchi bilan bog'laning.`);
    }
    Q('DELETE FROM players WHERE game_id = ? AND id = ?').run(g.id, u.id);
    Q('DELETE FROM cancellations WHERE game_id = ? AND user_id = ?').run(g.id, u.id);
    Q('INSERT INTO cancellations (game_id, user_id) VALUES (?,?)').run(g.id, u.id);
    Q('UPDATE coupons SET used_game_id = NULL WHERE used_game_id = ? AND user_id = ?').run(g.id, u.id);
  });
  return gameBody(u, ctx.params.id);
}

function addComment(ctx) {
  const u = requireUser(ctx);
  const g = getGameRow(ctx.params.id);
  const text = String(ctx.body.text || '').trim();
  if (!text) throw fail(400, 'invalid_comment', "Izoh bo'sh bo'lmasin.");
  if (text.length > 300) throw fail(400, 'invalid_comment', 'Izoh 300 belgidan oshmasin.');
  Q('INSERT INTO comments (id, game_id, user_id, user_name, text, created_at) VALUES (?,?,?,?,?,?)').run(uid(), g.id, u.id, u.name, text, now());
  return gameBody(u, g.id);
}

function deleteComment(ctx) {
  const u = requireUser(ctx);
  const g = getGameRow(ctx.params.id);
  const c = Q('SELECT * FROM comments WHERE id = ? AND game_id = ?').get(ctx.params.cid, g.id);
  if (!c) throw fail(404, 'not_found', 'Izoh topilmadi.');
  if (u.role !== 'ADMIN' && c.user_id !== u.id) throw fail(403, 'forbidden', "Bu izohni o'chira olmaysiz.");
  Q('DELETE FROM comments WHERE id = ?').run(c.id);
  return gameBody(u, g.id);
}

// ================================================================ Ishchi adminlar va o'yinchilar (faqat bosh admin)

function listStaff(ctx) {
  requireAdmin(ctx);
  const rows = Q("SELECT * FROM users WHERE role = 'STAFF' ORDER BY joined_at DESC").all();
  return { status: 200, body: { staff: rows.map(userJson) } };
}

/** Foydalanuvchining oxirgi bildirishnomalari (masalan, o'yin biriktirilgani haqida). */
function getNotifications(ctx) {
  const u = requireUser(ctx);
  const rows = Q('SELECT * FROM notifications WHERE user_id = ? ORDER BY created_at DESC LIMIT 50').all(u.id);
  return {
    status: 200,
    body: {
      notifications: rows.map((n) => ({ id: n.id, kind: n.kind, gameId: n.game_id || '', title: n.title, body: n.body, createdAt: n.created_at }))
    }
  };
}

function createStaff(ctx) {
  requireAdmin(ctx);
  const name = cleanName(ctx.body.name);
  const login = checkLogin(ctx.body.login);
  const password = checkPassword(ctx.body.password, login);
  if (Q('SELECT 1 FROM users WHERE login = ?').get(login)) throw fail(409, 'login_taken', 'Bu logon band. Boshqasini tanlang.');
  const id = insertStaffLike('STAFF', name, login, password);
  return { status: 200, body: { staff: userJson(Q('SELECT * FROM users WHERE id = ?').get(id)) } };
}

function deleteStaff(ctx) {
  requireAdmin(ctx);
  const s = Q("SELECT * FROM users WHERE id = ? AND role = 'STAFF'").get(ctx.params.id);
  if (!s) throw fail(404, 'not_found', 'Ishchi admin topilmadi.');
  tx(() => {
    Q('DELETE FROM sessions WHERE user_id = ?').run(s.id);
    Q('DELETE FROM game_staff WHERE user_id = ?').run(s.id);
    Q('DELETE FROM notifications WHERE user_id = ?').run(s.id);
    deleteChatData(s.id);
    Q('DELETE FROM users WHERE id = ?').run(s.id);
  });
  return { status: 200, body: { ok: true } };
}

function listPlayers(ctx) {
  requireAdmin(ctx);
  const rows = Q("SELECT * FROM users WHERE role = 'USER' ORDER BY name COLLATE NOCASE").all();
  return {
    status: 200,
    body: { players: rows.map((u) => ({ id: u.id, name: u.name, phone: fmtPhone(u.phone), login: u.login, joinedAt: u.joined_at, avatar: u.avatar || '' })) }
  };
}

// ================================================================ Kuponlar va reyting

function rankingForMonth(month) {
  const t = now();
  const stats = new Map();
  for (const g of Q('SELECT * FROM games WHERE is_cancelled = 0').all()) {
    if (monthKeyOf(g.date_millis) !== month) continue;
    const over = isOver(g, t);
    for (const p of Q('SELECT * FROM players WHERE game_id = ?').all(g.id)) {
      if (p.id.startsWith('guest-')) continue;
      const s = stats.get(p.id) || { id: p.id, name: p.name, signed: 0, played: 0 };
      s.signed += 1;
      s.name = p.name;
      if (over && p.attended !== 0) s.played += 1;
      stats.set(p.id, s);
    }
  }
  return [...stats.values()].filter((s) => s.played > 0)
    .sort((a, b) => b.played - a.played || b.signed - a.signed || a.name.toLowerCase().localeCompare(b.name.toLowerCase()));
}

/** O'tgan oylar uchun hali berilmagan kuponlarni beradi (har oy faqat bir marta). */
function issueMonthlyCoupons() {
  const current = monthKeyOf(now());
  const months = new Set(Q('SELECT date_millis FROM games').all().map((r) => monthKeyOf(r.date_millis)));
  for (const month of months) {
    if (month >= current) continue;
    if (Q('SELECT 1 FROM issued_months WHERE month_key = ?').get(month)) continue;
    tx(() => {
      for (const s of rankingForMonth(month).slice(0, 3)) {
        if (Q('SELECT 1 FROM users WHERE id = ?').get(s.id)) {
          Q('INSERT INTO coupons (id, user_id, month_key, used_game_id) VALUES (?,?,?,NULL)').run(uid(), s.id, month);
        }
      }
      Q('INSERT INTO issued_months (month_key) VALUES (?)').run(month);
    });
  }
}

function getCoupons(ctx) {
  const u = requireUser(ctx);
  issueMonthlyCoupons();
  const rows = Q('SELECT * FROM coupons WHERE user_id = ? ORDER BY month_key DESC').all(u.id);
  return { status: 200, body: { coupons: rows.map((c) => ({ id: c.id, userId: c.user_id, monthKey: c.month_key, usedGameId: c.used_game_id })) } };
}

// ================================================================ Sozlamalar (bog'lanish)

function getSetting(key) {
  const r = Q('SELECT value FROM settings WHERE key = ?').get(key);
  return r ? r.value : '';
}

function getSettings(ctx) {
  requireUser(ctx);
  return { status: 200, body: { supportPhone: getSetting('support_phone'), supportTelegram: getSetting('support_telegram') } };
}

function putSettings(ctx) {
  requireAdmin(ctx);
  const phone = String(ctx.body.supportPhone || '').trim().slice(0, 20);
  const tg = String(ctx.body.supportTelegram || '').trim().replace(/^@/, '').replace(/^https:\/\/t\.me\//, '').slice(0, 40);
  if (tg && !/^[A-Za-z0-9_]{3,40}$/.test(tg)) throw fail(400, 'invalid_telegram', "Telegram username noto'g'ri.");
  const up = Q('INSERT INTO settings (key, value) VALUES (?,?) ON CONFLICT(key) DO UPDATE SET value = excluded.value');
  up.run('support_phone', phone);
  up.run('support_telegram', tg);
  return { status: 200, body: { supportPhone: phone, supportTelegram: tg } };
}

// ================================================================ Rasmlar

const MAX_AVATAR_BYTES = 3 * 1024 * 1024;

/** Rasm turini fayl boshidagi belgilar bo'yicha aniqlaydi (JPG, PNG yoki WEBP). */
function detectImageExt(buf) {
  if (buf.length > 12 && buf[0] === 0xff && buf[1] === 0xd8 && buf[2] === 0xff) return 'jpg';
  if (buf.length > 12 && buf.slice(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))) return 'png';
  if (buf.length > 12 && buf.slice(0, 4).toString('latin1') === 'RIFF' && buf.slice(8, 12).toString('latin1') === 'WEBP') return 'webp';
  return null;
}

/** Rasmni tekshirib, uploads papkasiga saqlaydi va "/uploads/..." havolasini qaytaradi. */
function storeImage(buf) {
  const ext = detectImageExt(buf);
  if (!ext) throw fail(400, 'invalid_image', 'Faqat JPG, PNG yoki WEBP rasm yuklash mumkin.');
  const name = `${uid()}.${ext}`;
  fs.writeFileSync(path.join(UPLOAD_DIR, name), buf);
  return `/uploads/${name}`;
}

/** Serverdagi rasm faylini o'chiradi (faqat uploads ichidagi to'g'ri nomli fayl). */
function removeUploadFile(url) {
  if (!url || !String(url).startsWith('/uploads/')) return;
  const name = path.basename(String(url));
  if (!/^[a-f0-9-]{36}\.(jpg|png|webp)$/.test(name)) return;
  try { fs.unlinkSync(path.join(UPLOAD_DIR, name)); } catch (_) { /* fayl yo'q bo'lsa e'tibor bermaymiz */ }
}

async function uploadImage(ctx) {
  requireAdmin(ctx);
  const buf = await readBody(ctx.req, MAX_UPLOAD_BYTES);
  return { status: 200, body: { url: storeImage(buf) } };
}

/** Har bir foydalanuvchi o'z profil rasmini yuklaydi (eskisi o'chiriladi). */
async function setAvatar(ctx) {
  const u = requireUser(ctx);
  const buf = await readBody(ctx.req, MAX_AVATAR_BYTES);
  const url = storeImage(buf);
  Q('UPDATE users SET avatar = ? WHERE id = ?').run(url, u.id);
  removeUploadFile(u.avatar);
  return { status: 200, body: { user: userJson(Q('SELECT * FROM users WHERE id = ?').get(u.id)) } };
}

function deleteAvatar(ctx) {
  const u = requireUser(ctx);
  Q("UPDATE users SET avatar = '' WHERE id = ?").run(u.id);
  removeUploadFile(u.avatar);
  return { status: 200, body: { user: userJson(Q('SELECT * FROM users WHERE id = ?').get(u.id)) } };
}

const MIME = { jpg: 'image/jpeg', png: 'image/png', webp: 'image/webp' };

function serveUpload(ctx) {
  const name = ctx.params.name;
  if (!/^[a-f0-9-]{36}\.(jpg|png|webp)$/.test(name)) throw fail(404, 'not_found', 'Topilmadi.');
  const file = path.join(UPLOAD_DIR, name);
  if (!fs.existsSync(file)) throw fail(404, 'not_found', 'Topilmadi.');
  const data = fs.readFileSync(file);
  ctx.res.writeHead(200, {
    'Content-Type': MIME[name.split('.').pop()],
    'Content-Length': data.length,
    'Cache-Control': 'public, max-age=31536000, immutable',
    'X-Content-Type-Options': 'nosniff'
  });
  ctx.res.end(data);
  return null;
}

/** Hech bir o'yinga bog'lanmagan eski (1 kundan oshgan) rasmlarni o'chiradi. */
function sweepOrphans() {
  try {
    const used = new Set();
    for (const g of Q('SELECT images FROM games').all()) for (const im of JSON.parse(g.images || '[]')) used.add(path.basename(im));
    for (const r of Q("SELECT avatar FROM users WHERE avatar != ''").all()) used.add(path.basename(r.avatar));
    for (const f of fs.readdirSync(UPLOAD_DIR)) {
      if (used.has(f)) continue;
      const full = path.join(UPLOAD_DIR, f);
      if (now() - fs.statSync(full).mtimeMs > 24 * 3600 * 1000) fs.unlinkSync(full);
    }
  } catch (e) { console.error('[tozalash xatosi]', e.message); }
}

// ================================================================ Marshrutlar

const routes = [];
const add = (method, pattern, handler, opts = {}) => {
  const keys = [];
  const re = new RegExp('^' + pattern.replace(/:([a-z]+)/g, (_, k) => { keys.push(k); return '([^/]+)'; }) + '$');
  routes.push({ method, re, keys, handler, raw: !!opts.raw });
};

// ------------------------------------------------------------------ PUSH (Firebase Cloud Messaging)
// Telefonga ilova yopiq bo'lsa ham darhol xabar yuborish. Firebase'dan olingan "service account" fayli kerak
// (backend/firebase-service-account.json yoki FIREBASE_SERVICE_ACCOUNT muhit o'zgaruvchisi). Fayl bo'lmasa, push
// o'chiq turadi, server esa avvalgidek ishlayveradi.
const FCM_KEY_PATH = process.env.FIREBASE_SERVICE_ACCOUNT || path.join(__dirname, 'firebase-service-account.json');
const FCM_BASE = process.env.FCM_ENDPOINT || 'https://fcm.googleapis.com';
let fcmAccount;          // undefined = hali o'qilmagan, false = o'chiq
let fcmAccess = { value: null, exp: 0 };

function loadFcm() {
  if (fcmAccount !== undefined) return fcmAccount || null;
  try {
    const j = JSON.parse(fs.readFileSync(FCM_KEY_PATH, 'utf8'));
    if (!j.client_email || !j.private_key || !j.project_id) throw new Error("faylda client_email/private_key/project_id yo'q");
    fcmAccount = j;
    console.log(`[push] yoqilgan (loyiha: ${j.project_id})`);
  } catch (e) {
    fcmAccount = false;
    console.log(`[push] o'chiq: ${FCM_KEY_PATH} o'qilmadi (${e.code === 'ENOENT' ? 'fayl topilmadi' : e.message}). Xabarlar faqat ilova ochiq paytda keladi.`);
  }
  return fcmAccount || null;
}

async function fcmAccessToken(acc) {
  if (fcmAccess.value && Date.now() < fcmAccess.exp - 60000) return fcmAccess.value;
  const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');
  const aud = acc.token_uri || 'https://oauth2.googleapis.com/token';
  const iat = Math.floor(Date.now() / 1000);
  const header = b64({ alg: 'RS256', typ: 'JWT' });
  const claim = b64({ iss: acc.client_email, scope: 'https://www.googleapis.com/auth/firebase.messaging', aud, iat, exp: iat + 3600 });
  const sig = crypto.createSign('RSA-SHA256').update(`${header}.${claim}`).sign(acc.private_key).toString('base64url');
  const res = await fetch(aud, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion: `${header}.${claim}.${sig}` })
  });
  const j = await res.json().catch(() => ({}));
  if (!res.ok || !j.access_token) throw new Error(`Google token xatosi: ${j.error_description || j.error || res.status}`);
  fcmAccess = { value: j.access_token, exp: Date.now() + (j.expires_in || 3600) * 1000 };
  return fcmAccess.value;
}

async function fcmSend(acc, accessToken, token, data) {
  const res = await fetch(`${FCM_BASE}/v1/projects/${acc.project_id}/messages:send`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ message: { token, data, android: { priority: 'HIGH' } } })
  });
  if (res.ok) return;
  const j = await res.json().catch(() => ({}));
  const code = (j.error && j.error.details && j.error.details.map((d) => d.errorCode).find(Boolean)) || (j.error && j.error.status) || res.status;
  if (res.status === 404 || code === 'UNREGISTERED') {
    Q('DELETE FROM push_tokens WHERE token = ?').run(token); // telefon ilovani o'chirgan
  } else {
    console.error(`[push] yuborilmadi (${code}): ${(j.error && j.error.message) || ''}`);
  }
}

/** Foydalanuvchilarning barcha telefonlariga xabar yuboradi (javobni kutmaydi). [data] qiymatlari matn bo'lishi kerak. */
function pushToUsers(userIds, data) {
  const acc = loadFcm();
  if (!acc || !userIds.length) return;
  const payload = Object.fromEntries(Object.entries(data).map(([k, v]) => [k, String(v == null ? '' : v)]));
  const tokens = Q(`SELECT token FROM push_tokens WHERE user_id IN (${userIds.map(() => '?').join(',')})`).all(...userIds).map((r) => r.token);
  if (!tokens.length) return;
  (async () => {
    try {
      const at = await fcmAccessToken(acc);
      for (const t of tokens) await fcmSend(acc, at, t, payload);
    } catch (e) {
      console.error('[push] xato:', e.message);
    }
  })();
}

function savePushToken(ctx) {
  const u = requireUser(ctx);
  const token = String(ctx.body.token || '').trim();
  if (token.length < 20 || token.length > 4096) throw fail(400, 'bad_token', "Noto'g'ri token.");
  Q('INSERT INTO push_tokens (token, user_id, updated_at) VALUES (?,?,?) ON CONFLICT(token) DO UPDATE SET user_id = excluded.user_id, updated_at = excluded.updated_at')
    .run(token, u.id, now());
  return { status: 200, body: { ok: true } };
}

function removePushToken(ctx) {
  const u = requireUser(ctx);
  Q('DELETE FROM push_tokens WHERE token = ? AND user_id = ?').run(String(ctx.body.token || ''), u.id);
  return { status: 200, body: { ok: true } };
}

const gameWhenText = (g) => {
  const d = new Date(g.date_millis + 5 * 3600 * 1000); // Toshkent vaqti
  return `${String(d.getUTCDate()).padStart(2, '0')}.${String(d.getUTCMonth() + 1).padStart(2, '0')} · ${g.start_time}`;
};

/** O'yinchilarga (akkaunti borlarga) xabar: ilova ichidagi ro'yxatga yoziladi va push yuboriladi. */
function notifyGamePlayers(g, kind, title, body) {
  const ids = Q("SELECT id FROM players WHERE game_id = ? AND id NOT LIKE 'guest-%'").all(g.id).map((r) => r.id);
  for (const id of ids) {
    const nid = uid();
    Q('INSERT INTO notifications (id, user_id, kind, game_id, title, body, created_at) VALUES (?,?,?,?,?,?,?)').run(nid, id, kind, g.id, title, body, now());
    pushToUsers([id], { type: 'notif', id: 'srv:' + nid, kind, gameId: g.id, title, body, tab: 0 });
  }
}

const chatPreview = (t) => (t.length > 120 ? t.slice(0, 117) + '…' : t);

// ------------------------------------------------------------------ CHAT
// Umumiy guruh ("group") va shaxsiy chatlar ("dm:<id>|<id>"). Chatdan foydalanish uchun foydalanuvchi
// avval "qo'shilaman" deb javob berishi kerak (status: none | joined | declined).
const CHAT_MAX_LEN = 1000;
let lastChatTs = 0;
const chatNow = () => { lastChatTs = Math.max(now(), lastChatTs + 1); return lastChatTs; }; // har xabar vaqti noyob
const chatHits = new Map();

function chatStatus(userId) {
  const r = Q('SELECT status FROM chat_members WHERE user_id = ?').get(userId);
  return r ? r.status : 'none';
}

function requireChatMember(ctx) {
  const u = requireUser(ctx);
  if (chatStatus(u.id) !== 'joined') throw fail(403, 'chat_not_joined', "Avval chatga qo'shiling.");
  return u;
}

const dmRoom = (a, b) => 'dm:' + [a, b].sort().join('|');

const CHAT_SELECT = 'SELECT m.*, u.name AS name, u.avatar AS avatar, u.role AS role FROM chat_messages m LEFT JOIN users u ON u.id = m.sender_id';

function chatMsgJson(m) {
  return {
    id: m.id, senderId: m.sender_id, senderName: m.name || 'Foydalanuvchi', senderAvatar: m.avatar || '',
    senderRole: m.role || 'USER', text: m.text, createdAt: m.created_at
  };
}

function roomMessages(room, after) {
  if (after > 0) {
    return Q(`${CHAT_SELECT} WHERE m.room = ? AND m.created_at > ? ORDER BY m.created_at ASC LIMIT 200`).all(room, after).map(chatMsgJson);
  }
  return Q(`${CHAT_SELECT} WHERE m.room = ? ORDER BY m.created_at DESC LIMIT 100`).all(room).reverse().map(chatMsgJson);
}

function markRead(userId, room) {
  Q('INSERT INTO chat_reads (user_id, room, read_at) VALUES (?,?,?) ON CONFLICT(user_id, room) DO UPDATE SET read_at = excluded.read_at')
    .run(userId, room, Math.max(now(), lastChatTs));
}

function unreadFor(userId, room) {
  const r = Q('SELECT read_at FROM chat_reads WHERE user_id = ? AND room = ?').get(userId, room);
  return Q('SELECT COUNT(*) AS c FROM chat_messages WHERE room = ? AND created_at > ? AND sender_id != ?').get(room, r ? r.read_at : 0, userId).c;
}

function myDmRooms(userId) {
  return Q("SELECT DISTINCT room FROM chat_messages WHERE room LIKE 'dm:%' AND instr(room, ?) > 0").all(userId).map((r) => r.room)
    .filter((room) => room.slice(3).split('|').includes(userId));
}

function chatPublicUser(row) {
  return { id: row.id, name: row.name, avatar: row.avatar || '', role: row.role };
}

function chatRate(userId) {
  const t = now();
  const list = (chatHits.get(userId) || []).filter((x) => t - x < 60000);
  if (list.length >= 30) throw fail(429, 'chat_rate', "Juda tez yozyapsiz. Biroz kuting.");
  list.push(t);
  chatHits.set(userId, list);
}

function cleanChatText(v) {
  const text = String(v || '').replace(/\r/g, '').trim();
  if (!text) throw fail(400, 'empty_message', "Xabar bo'sh bo'lishi mumkin emas.");
  if (text.length > CHAT_MAX_LEN) throw fail(400, 'message_too_long', `Xabar ${CHAT_MAX_LEN} belgidan oshmasin.`);
  return text;
}

function chatGetStatus(ctx) {
  const u = requireUser(ctx);
  const status = chatStatus(u.id);
  let groupUnread = 0;
  let dmUnread = 0;
  if (status === 'joined') {
    groupUnread = unreadFor(u.id, 'group');
    for (const room of myDmRooms(u.id)) dmUnread += unreadFor(u.id, room);
  }
  return { status: 200, body: { status, groupUnread, dmUnread } };
}

function chatSetStatus(status) {
  return (ctx) => {
    const u = requireUser(ctx);
    Q('INSERT INTO chat_members (user_id, status, updated_at) VALUES (?,?,?) ON CONFLICT(user_id) DO UPDATE SET status = excluded.status, updated_at = excluded.updated_at')
      .run(u.id, status, now());
    if (status === 'joined') markRead(u.id, 'group'); // eski xabarlar "o'qilmagan" bo'lib chiqmasin
    return { status: 200, body: { status } };
  };
}

function chatMembers(ctx) {
  const u = requireChatMember(ctx);
  const rows = Q("SELECT u.id, u.name, u.avatar, u.role FROM chat_members c JOIN users u ON u.id = c.user_id WHERE c.status = 'joined' AND u.id != ? ORDER BY u.name COLLATE NOCASE").all(u.id);
  const total = Q("SELECT COUNT(*) AS c FROM chat_members WHERE status = 'joined'").get().c;
  return { status: 200, body: { members: rows.map(chatPublicUser), total } };
}

function chatGroupGet(ctx) {
  const u = requireChatMember(ctx);
  const after = Number(ctx.query.get('after')) || 0;
  const messages = roomMessages('group', after);
  markRead(u.id, 'group');
  return { status: 200, body: { messages } };
}

function chatGroupPost(ctx) {
  const u = requireChatMember(ctx);
  const text = cleanChatText(ctx.body.text);
  chatRate(u.id);
  const id = uid();
  Q('INSERT INTO chat_messages (id, room, sender_id, text, created_at) VALUES (?,?,?,?,?)').run(id, 'group', u.id, text, chatNow());
  const members = Q("SELECT user_id FROM chat_members WHERE status = 'joined' AND user_id != ?").all(u.id).map((r) => r.user_id);
  pushToUsers(members, { type: 'chat', title: `Umumiy chat · ${u.name}`, body: chatPreview(text), room: 'group', tab: 2 });
  const m = Q(`${CHAT_SELECT} WHERE m.id = ?`).get(id);
  return { status: 200, body: { message: chatMsgJson(m) } };
}

function chatDmList(ctx) {
  const u = requireChatMember(ctx);
  const out = [];
  for (const room of myDmRooms(u.id)) {
    const peerId = room.slice(3).split('|').find((x) => x !== u.id);
    const peer = Q('SELECT id, name, avatar, role FROM users WHERE id = ?').get(peerId);
    if (!peer) continue;
    const last = Q(`${CHAT_SELECT} WHERE m.room = ? ORDER BY m.created_at DESC LIMIT 1`).get(room);
    out.push({ peer: chatPublicUser(peer), last: last ? chatMsgJson(last) : null, unread: unreadFor(u.id, room), updatedAt: last ? last.created_at : 0 });
  }
  out.sort((a, b) => b.updatedAt - a.updatedAt);
  return { status: 200, body: { conversations: out } };
}

function chatPeer(ctx, me) {
  const peer = Q('SELECT id, name, avatar, role FROM users WHERE id = ?').get(ctx.params.id);
  if (!peer) throw fail(404, 'not_found', 'Foydalanuvchi topilmadi.');
  if (peer.id === me.id) throw fail(400, 'self_chat', "O'zingizga yozib bo'lmaydi.");
  return peer;
}

function chatDmGet(ctx) {
  const u = requireChatMember(ctx);
  const peer = chatPeer(ctx, u);
  const room = dmRoom(u.id, peer.id);
  const after = Number(ctx.query.get('after')) || 0;
  const messages = roomMessages(room, after);
  markRead(u.id, room);
  return { status: 200, body: { peer: chatPublicUser(peer), peerInChat: chatStatus(peer.id) === 'joined', messages } };
}

function chatDmPost(ctx) {
  const u = requireChatMember(ctx);
  const peer = chatPeer(ctx, u);
  if (chatStatus(peer.id) !== 'joined') throw fail(403, 'peer_not_in_chat', "Bu foydalanuvchi chatga qo'shilmagan.");
  const text = cleanChatText(ctx.body.text);
  chatRate(u.id);
  const id = uid();
  Q('INSERT INTO chat_messages (id, room, sender_id, text, created_at) VALUES (?,?,?,?,?)').run(id, dmRoom(u.id, peer.id), u.id, text, chatNow());
  pushToUsers([peer.id], { type: 'chat', title: u.name, body: chatPreview(text), room: 'dm:' + u.id, tab: 2 });
  const m = Q(`${CHAT_SELECT} WHERE m.id = ?`).get(id);
  return { status: 200, body: { message: chatMsgJson(m) } };
}

/** Xabarni o'chirish: o'z xabarini hamma; umumiy guruhda esa bosh admin istalganini. */
function chatDelete(ctx) {
  const u = requireChatMember(ctx);
  const m = Q('SELECT * FROM chat_messages WHERE id = ?').get(ctx.params.id);
  if (!m) throw fail(404, 'not_found', 'Xabar topilmadi.');
  const mine = m.sender_id === u.id;
  const adminOnGroup = u.role === 'ADMIN' && m.room === 'group';
  if (!mine && !adminOnGroup) throw fail(403, 'forbidden', "Bu xabarni o'chirib bo'lmaydi.");
  Q('DELETE FROM chat_messages WHERE id = ?').run(m.id);
  return { status: 200, body: { ok: true } };
}

function deleteChatData(userId) {
  Q('DELETE FROM push_tokens WHERE user_id = ?').run(userId);
  Q('DELETE FROM chat_members WHERE user_id = ?').run(userId);
  Q('DELETE FROM chat_reads WHERE user_id = ?').run(userId);
  Q("DELETE FROM chat_messages WHERE sender_id = ? OR (room LIKE 'dm:%' AND instr(room, ?) > 0)").run(userId, userId);
}

add('GET', '/health', () => ({ status: 200, body: { ok: true, dev: DEV_MODE, otpRequired: OTP_REQUIRED } }));

/** Ochiq sahifalar (Google Play talab qiladi): maxfiylik siyosati va akkauntni o'chirish. */
function legalPage(title, bodyHtml) {
  return `<!doctype html><html lang="uz"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>${title} | Treble</title>
<style>
body{margin:0;background:#0f1115;color:#e8eaed;font:16px/1.65 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
main{max-width:720px;margin:0 auto;padding:28px 18px 60px}
h1{font-size:26px;margin:0 0 4px}h2{font-size:18px;margin:28px 0 6px}
p,li{color:#c4c7cc}.muted{color:#8a8f98;font-size:14px}a{color:#7cc4ff}
</style></head><body><main>${bodyHtml}</main></body></html>`;
}
function contactLine() {
  const ph = getSetting('support_phone'), tg = getSetting('support_telegram');
  const parts = [];
  if (tg) parts.push(`Telegram: <a href="https://t.me/${tg}">@${tg}</a>`);
  if (ph) parts.push(`Telefon: ${ph}`);
  return parts.length ? parts.join(' &nbsp;|&nbsp; ') : 'Ilovadagi «Bog\'lanish» bo\'limi orqali';
}
function sendHtml(ctx, html) {
  ctx.res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'public, max-age=300' });
  ctx.res.end(html);
  return null;
}
add('GET', '/privacy', (ctx) => sendHtml(ctx, legalPage('Maxfiylik siyosati', `
<h1>Maxfiylik siyosati</h1>
<p class="muted">Treble ilovasi. Oxirgi yangilanish: 2026-yil 11-noyabr.</p>
<p>Treble — futbol o'yinlarini topish va band qilish ilovasi. Ushbu sahifa ilova qanday ma'lumotlarni yig'ishi va ulardan qanday foydalanishini tushuntiradi.</p>
<h2>Qanday ma'lumotlar yig'iladi</h2>
<ul>
<li>Ismingiz, telefon raqamingiz va tizimga kirish nomingiz (login);</li>
<li>parolingiz (faqat shifrlangan ko'rinishda, asl holida saqlanmaydi);</li>
<li>profil rasmingiz (agar yuklasangiz);</li>
<li>band qilgan o'yinlaringiz, qoldirgan izohlaringiz va ilova ichidagi xabarlaringiz;</li>
<li>Telegram orqali tasdiqlash ishlatilsa, Telegram foydalanuvchi identifikatori;</li>
<li>push-bildirishnomalar yuborish uchun qurilmaning bildirishnoma tokeni.</li>
</ul>
<h2>Ma'lumotlardan nima uchun foydalaniladi</h2>
<p>Faqat ilova ishlashi uchun: hisob yaratish va tizimga kirish, telefon raqamini Telegram bot orqali tasdiqlash, o'yinlarga yozilish, o'yin oldidan eslatma va bildirishnomalar yuborish. Ma'lumotlaringiz reklama uchun ishlatilmaydi va sotilmaydi.</p>
<h2>Telefon raqamingizni kim ko'radi</h2>
<p>Faqat o'yinlarni tashkil qiluvchi administratorlar. Boshqa futbolchilar faqat ismingizni ko'radi.</p>
<h2>Uchinchi tomon xizmatlari</h2>
<p>Push-bildirishnomalar uchun Google Firebase Cloud Messaging, tasdiqlash kodini yuborish uchun Telegram ishlatiladi. Boshqa uchinchi shaxslarga ma'lumot berilmaydi.</p>
<h2>Saqlash muddati</h2>
<p>Hisob ma'lumotlari akkauntingiz o'chirilguncha saqlanadi. Bildirishnomalar 30 kundan, ilova ichidagi xabarlar 180 kundan keyin avtomatik o'chiriladi. Ma'lumotlar HTTPS orqali shifrlangan kanal bilan uzatiladi.</p>
<h2>Ma'lumotlarni o'chirish</h2>
<p>Akkauntni va unga bog'liq ma'lumotlarni istalgan vaqtda ilovaning Profil bo'limidagi «Akkauntni o'chirish» orqali o'chirishingiz mumkin. Batafsil: <a href="/delete-account">akkauntni o'chirish sahifasi</a>.</p>
<h2>Bolalar</h2>
<p>Ilova 13 yoshgacha bo'lgan bolalarga mo'ljallanmagan.</p>
<h2>Bog'lanish</h2>
<p>${contactLine()}</p>
`)));
add('GET', '/delete-account', (ctx) => sendHtml(ctx, legalPage('Akkauntni o\'chirish', `
<h1>Akkauntni o'chirish</h1>
<p class="muted">Treble ilovasi.</p>
<h2>Ilova orqali (tavsiya etiladi)</h2>
<ol>
<li>Treble ilovasini oching va hisobingizga kiring.</li>
<li>Pastdan «Profil» bo'limini oching.</li>
<li>«Akkauntni o'chirish» ni bosing va parolingizni kiriting.</li>
</ol>
<h2>Nima o'chiriladi</h2>
<p>Ismingiz, telefon raqamingiz, loginingiz, parolingiz, profil rasmingiz, bildirishnomalaringiz, ilova ichidagi xabarlaringiz, kuponlaringiz va hali o'tmagan o'yinlarga yozilishingiz butunlay o'chiriladi.</p>
<h2>Ilovaga kira olmasangiz</h2>
<p>O'chirish so'rovini yuboring, telefon raqamingizni ko'rsating. So'rov 7 kun ichida bajariladi: ${contactLine()}</p>
`)));
add('POST', '/otp/send', otpSend);
add('POST', '/otp/verify', otpVerify);
add('POST', '/auth/register', register);
add('POST', '/auth/login', login);
add('POST', '/auth/reset', resetPassword);
add('POST', '/auth/logout', logout);
add('POST', '/auth/telegram', telegramAuth);
add('GET', '/app', serveMiniApp);
add('GET', '/me', getMe);
add('PUT', '/me', updateMe);
add('POST', '/me/avatar', setAvatar, { raw: true });
add('DELETE', '/me/avatar', deleteAvatar);
add('POST', '/me/password', changePassword);
add('POST', '/me/delete', deleteMe);
add('GET', '/games', getGames);
add('POST', '/games', createGame);
add('PUT', '/games/:id', editGame);
add('DELETE', '/games/:id', deleteGame);
add('POST', '/games/:id/cancel', setFlag('is_cancelled'));
add('POST', '/games/:id/finish', setFlag('is_finished'));
add('POST', '/games/:id/book', book);
add('DELETE', '/games/:id/book', cancelBooking);
add('POST', '/games/:id/comments', addComment);
add('DELETE', '/games/:id/comments/:cid', deleteComment);
add('POST', '/games/:id/guests', addGuest);
add('DELETE', '/games/:id/players/:pid', adminPlayerAction((g, p) => {
  Q('DELETE FROM players WHERE game_id = ? AND id = ?').run(g.id, p.id);
  Q('UPDATE coupons SET used_game_id = NULL WHERE used_game_id = ? AND user_id = ?').run(g.id, p.id);
}));
add('POST', '/games/:id/players/:pid/attendance', adminPlayerAction((g, p, b) => {
  const v = b.attended === true ? 1 : b.attended === false ? 0 : null;
  Q('UPDATE players SET attended = ? WHERE game_id = ? AND id = ?').run(v, g.id, p.id);
}, { staffAllowed: true }));
add('POST', '/games/:id/players/:pid/paid', adminPlayerAction((g, p, b) => {
  Q('UPDATE players SET paid = ? WHERE game_id = ? AND id = ?').run(b.paid ? 1 : 0, g.id, p.id);
}, { staffAllowed: true }));
add('GET', '/staff', listStaff);
add('GET', '/notifications', getNotifications);
add('POST', '/push/token', savePushToken);
add('POST', '/push/unregister', removePushToken);
add('GET', '/chat/status', chatGetStatus);
add('POST', '/chat/join', chatSetStatus('joined'));
add('POST', '/chat/decline', chatSetStatus('declined'));
add('GET', '/chat/members', chatMembers);
add('GET', '/chat/group', chatGroupGet);
add('POST', '/chat/group', chatGroupPost);
add('GET', '/chat/dms', chatDmList);
add('GET', '/chat/dm/:id', chatDmGet);
add('POST', '/chat/dm/:id', chatDmPost);
add('DELETE', '/chat/messages/:id', chatDelete);
add('POST', '/staff', createStaff);
add('DELETE', '/staff/:id', deleteStaff);
add('GET', '/players', listPlayers);
add('GET', '/coupons', getCoupons);
add('GET', '/settings', getSettings);
add('PUT', '/settings', putSettings);
add('POST', '/upload', uploadImage, { raw: true });
add('GET', '/uploads/:name', serveUpload);

// ================================================================ Telegram Mini App
Q('CREATE TABLE IF NOT EXISTS tg_users (tg_id TEXT PRIMARY KEY, user_id TEXT NOT NULL)').run();
Q('CREATE TABLE IF NOT EXISTS tg_phones (tg_id TEXT PRIMARY KEY, phone TEXT NOT NULL)').run();

/** Mini App yuborgan initData ni bot tokeni bilan tekshiradi (soxtalashtirib bo'lmaydi). */
function tgVerify(initData) {
  const p = new URLSearchParams(String(initData || ''));
  const hash = p.get('hash');
  p.delete('hash');
  if (!hash || !TELEGRAM_BOT_TOKEN) return null;
  const str = [...p.entries()].map(([k, v]) => k + '=' + v).sort().join('\n');
  const secret = crypto.createHmac('sha256', 'WebAppData').update(TELEGRAM_BOT_TOKEN).digest();
  const calc = Buffer.from(crypto.createHmac('sha256', secret).update(str).digest('hex'));
  const got = Buffer.from(hash);
  if (calc.length !== got.length || !crypto.timingSafeEqual(calc, got)) return null;
  if (now() / 1000 - Number(p.get('auth_date') || 0) > 86400) return null;
  try { return JSON.parse(p.get('user')); } catch { return null; }
}

function telegramAuth(ctx) {
  const t = tgVerify(ctx.body.initData);
  if (!t || !t.id) throw fail(401, 'tg_invalid', "Telegram ma'lumoti tasdiqlanmadi. Mini App'ni bot orqali oching.");
  const tid = String(t.id);
  const link = Q('SELECT user_id FROM tg_users WHERE tg_id = ?').get(tid);
  let user = link && Q('SELECT * FROM users WHERE id = ?').get(link.user_id);
  if (!user) {
    const ph = Q('SELECT phone FROM tg_phones WHERE tg_id = ?').get(tid);
    if (!ph) return { status: 200, body: { needPhone: true } };
    user = Q('SELECT * FROM users WHERE phone = ?').get(ph.phone); // Android'dagi hisob bilan bog'lanadi
    if (!user) {
      const salt = crypto.randomBytes(16).toString('hex');
      const id = uid();
      let name = [t.first_name, t.last_name].filter(Boolean).join(' ').trim().slice(0, 40);
      if (name.length < 2) name = 'Futbolchi';
      Q("INSERT INTO users (id, name, phone, login, pass_hash, salt, role, joined_at) VALUES (?,?,?,?,?,?, 'USER', ?)")
        .run(id, name, ph.phone, 'tg' + tid, hashPassword(crypto.randomBytes(24).toString('hex'), salt), salt, now());
      user = Q('SELECT * FROM users WHERE id = ?').get(id);
    }
    Q('INSERT OR REPLACE INTO tg_users (tg_id, user_id) VALUES (?,?)').run(tid, user.id);
  }
  return { status: 200, body: authBody(user) };
}

function serveMiniApp(ctx) {
  const html = fs.readFileSync(path.join(__dirname, 'miniapp', 'index.html'));
  ctx.res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-cache' });
  ctx.res.end(html);
  return null;
}

async function tgApi(method, body) {
  const r = await fetch('https://api.telegram.org/bot' + TELEGRAM_BOT_TOKEN + '/' + method, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body), signal: AbortSignal.timeout(40000)
  });
  return r.json();
}

/** Bot: /start -> Mini App tugmasi; ulashilgan kontakt -> telefon raqamini saqlaydi. */
async function botLoop() {
  let offset = 0;
  try {
    const me = await tgApi('getMe', {});
    if (me.ok && me.result && me.result.username) BOT_USERNAME = BOT_USERNAME || me.result.username;
  } catch (e) { console.error('[bot getMe xatosi]', e.message); }
  console.log('Telegram bot ishga tushdi' + (MINIAPP_URL ? '' : ' (MINIAPP_URL berilmagan!)'));
  for (;;) {
    try {
      const r = await tgApi('getUpdates', { offset, timeout: 25, allowed_updates: ['message'] });
      for (const u of r.result || []) {
        offset = u.update_id + 1;
        const m = u.message;
        if (!m || !m.from) continue;
        if (m.contact && m.contact.user_id === m.from.id) {
          const d = String(m.contact.phone_number).replace(/\D/g, '');
          const phone = normalizePhone(d.slice(-9)) || d;
          Q('INSERT OR REPLACE INTO tg_phones (tg_id, phone) VALUES (?,?)').run(String(m.from.id), phone);
          // Ilovada shu raqam uchun kod so'ralgan va hali yuborilmagan bo'lsa, hozir yuboramiz
          let sent = 0;
          for (const purpose of ['register', 'reset']) {
            const rec = otps.get(`${purpose}:${phone}`);
            if (rec && rec.pendingCode && now() < rec.expiresAt) {
              const code = rec.pendingCode;
              delete rec.pendingCode;
              rec.expiresAt = now() + CODE_TTL_MS;
              rec.lastSentAt = now();
              await tgApi('sendMessage', { chat_id: m.chat.id, text: `🔐 Treble tasdiqlash kodi: ${code}\n\nKodni ilovaga kiriting. Hech kimga bermang.`, reply_markup: { remove_keyboard: true } });
              sent += 1;
              break;
            }
          }
          if (!sent) {
            await tgApi('sendMessage', { chat_id: m.chat.id, text: MINIAPP_URL ? "✅ Raqamingiz qabul qilindi. Agar ilovada kod kutayotgan bo'lsangiz va kod kelmagan bo'lsa: ilovada kiritgan raqamingiz shu Telegram akkauntdagi raqam bilan bir xil ekanini tekshiring, so'ng ilovada qayta kod so'rang. Treble'ni ochish:" : "✅ Raqamingiz qabul qilindi. Bu raqam uchun kutilayotgan kod topilmadi. Ilovada kiritgan raqamingiz shu Telegram akkauntdagi raqam bilan bir xil ekanini tekshiring (boshqa akkauntdan ochgan bo'lsangiz, to'g'risidan oching), so'ng ilovada qayta kod so'rang.",
              reply_markup: { remove_keyboard: true } });
          }
        } else if (m.text && m.text.startsWith('/start')) {
          if (MINIAPP_URL) {
            await tgApi('sendMessage', { chat_id: m.chat.id, text: "⚽ Treble: futbol o'yinlariga yoziling!",
              reply_markup: { inline_keyboard: [[{ text: "⚽ Treble'ni ochish", web_app: { url: MINIAPP_URL } }]] } });
          }
          await tgApi('sendMessage', { chat_id: m.chat.id, text: "Tasdiqlash kodini olish uchun telefon raqamingizni ulashing (pastdagi tugma):",
            reply_markup: { keyboard: [[{ text: '📱 Raqamni ulashish', request_contact: true }]], resize_keyboard: true, one_time_keyboard: true } });
        }
      }
    } catch (e) {
      console.error('[bot xatosi]', e.message);
      await new Promise((ok) => setTimeout(ok, 5000));
    }
  }
}

const server = http.createServer(async (req, res) => {
  try {
    const url = (req.url || '').split('?')[0];
    for (const r of routes) {
      if (r.method !== req.method) continue;
      const m = r.re.exec(url);
      if (!m) continue;
      const params = {};
      r.keys.forEach((k, i) => { params[k] = decodeURIComponent(m[i + 1]); });
      const qi = (req.url || '').indexOf('?');
      const query = new URLSearchParams(qi >= 0 ? req.url.slice(qi + 1) : '');
      const ctx = { req, res, params, query, ip: clientIp(req), user: authUser(req), body: {} };
      if (!r.raw && (req.method === 'POST' || req.method === 'PUT')) ctx.body = await readJson(req);
      const out = await r.handler(ctx);
      if (out === null) return; // handler javobni o'zi yubordi
      return sendJson(res, out.status, out.body);
    }
    return sendJson(res, 404, { error: 'not_found', message: 'Topilmadi.' });
  } catch (e) {
    if (e instanceof ApiError) return sendJson(res, e.status, { error: e.code, message: e.message, ...e.extra });
    console.error('[xato]', e);
    return sendJson(res, 500, { error: 'server_error', message: "Serverda xatolik yuz berdi. Keyinroq qayta urinib ko'ring." });
  }
});

seedAdmins();
issueMonthlyCoupons();
sweepOrphans();
setInterval(() => {
  issueMonthlyCoupons();
  sweepOrphans();
  Q('DELETE FROM notifications WHERE created_at < ?').run(now() - 30 * 24 * 3600 * 1000);
  Q('DELETE FROM chat_messages WHERE created_at < ?').run(now() - 180 * 24 * 3600 * 1000);
}, 60 * 60 * 1000).unref();

loadFcm(); // push yoqilganini ishga tushganda xabar qiladi
if (TELEGRAM_BOT_TOKEN) botLoop();

server.listen(PORT, HOST, () => {
  console.log(`Treble serveri ishga tushdi: http://localhost:${PORT}  (${DEV_MODE ? 'SINOV rejimi: kod yuborilmaydi, konsolda ko\'rinadi' : (OTP_VIA_BOT ? 'HAQIQIY rejim: kod o\'z Telegram botingiz orqali' : TELEGRAM_GATEWAY_TOKEN ? 'HAQIQIY rejim: kod Telegram orqali' : 'HAQIQIY SMS rejimi')})`);
});