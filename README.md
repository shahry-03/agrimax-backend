# 🌾 AgriMax — AI-Powered Smart Farming Platform

> **Voice-first, multilingual (Urdu + English) agricultural platform for Pakistani farmers.**
> Farmers can check crop prices, sell produce, manage their wallet, and get AI-powered advice — all through voice commands.

---

## ✨ Key Features

- 🎤 **Voice Assistant** — Speak commands in Urdu or English; AI understands and executes them
- 🤖 **Gemini AI Integration** — Intent detection, agricultural advice, and daily farming tips
- 💬 **Conversation Memory** — AI remembers context from your last 5 exchanges
- ⚡ **Response Caching** — Repeated queries skip the AI API for instant responses
- 📊 **Crop Prices** — Real-time market price lookup via voice
- 🛒 **Marketplace** — List and browse crop sell posts
- 👛 **Wallet** — Track your balance and transactions
- 🔐 **OTP Authentication** — Secure phone-number-based login
- 🌐 **Bilingual** — Full Urdu + English support throughout

---

## 🏗️ Architecture

```
agrimax-frontend/          → React 19 + Vite + TailwindCSS v4
agrimax_backend/
├── agrimax-app/           → Main Spring Boot app, security config, CORS
├── agrimax-common/        → Shared DTOs, events, exceptions
├── agrimax-identity/      → OTP auth, JWT, user management
├── agrimax-catalog/       → Crops master data, price management
├── agrimax-marketplace/   → Sell posts, bids, deals
├── agrimax-fintech/       → Wallet, transactions, escrow
├── agrimax-notification/  → Push/in-app notifications
└── agrimax-ai/            → Gemini AI integration, voice pipeline
```

**Voice Pipeline:** `Mic → Browser SpeechRecognition → POST /api/v1/voice/command → Gemini Intent Detection (with session memory + caching) → Command Handler → Bilingual Response → TTS`

---

## 🚀 Quick Start

### Prerequisites
- Java 21+
- Node.js 18+
- PostgreSQL (NeonDB recommended)
- Gemini API key ([Get one here](https://aistudio.google.com/))

### Backend Setup

```bash
cd agrimax_backend

# 1. Copy and fill in environment variables
cp .env.example .env
# Edit .env with your DB URL, JWT secret, and Gemini API key

# 2. Run the application
mvn spring-boot:run -pl agrimax-app
# OR open in IntelliJ IDEA and run AgriMaxApplication
```

### Frontend Setup

```bash
cd agrimax-frontend

# 1. Install dependencies
npm install

# 2. Copy env file (optional — defaults work with dev proxy)
cp .env.example .env.local

# 3. Start dev server
npm run dev
# Opens at http://localhost:3000
```

---

## 🔧 Environment Variables

### Backend (`agrimax_backend/.env`)

| Variable | Description | Example |
|---|---|---|
| `DB_URL` | NeonDB PostgreSQL JDBC URL | `jdbc:postgresql://...` |
| `DB_USERNAME` | Database username | `neondb_owner` |
| `DB_PASSWORD` | Database password | `your-password` |
| `JWT_SECRET` | Base64-encoded JWT secret (≥256 bits) | `YWdyaW1heC1...` |
| `GEMINI_API_KEY` | Google Gemini API key | `AIzaSy...` |
| `CORS_ALLOWED_ORIGINS` | Comma-separated allowed origins | `http://localhost:3000` |

### Frontend (`agrimax-frontend/.env.local`)

| Variable | Description | Default |
|---|---|---|
| `VITE_API_BASE_URL` | Backend URL (empty = use Vite proxy) | `` |

> **Note:** All Gemini API calls are routed through the backend. No API key is needed in the frontend.

---

## 🎤 Voice Commands

| Urdu | English | Action |
|---|---|---|
| `گندم کی قیمت بتاؤ` | `wheat price` | Show today's wheat price |
| `میری فصل بیچنی ہے` | `I want to sell my crop` | Create a sell listing |
| `50 کلو گندم 3000 میں` | `50 kg wheat at 3000` | List 50kg wheat at ₨3000 |
| `والیٹ دکھاؤ` | `show wallet` | View wallet balance |
| `لاگ ان کرو 03001234567` | `login 03001234567` | Send OTP to number |

---

## 🔒 Security Notes

- **`.env` is gitignored** — never commit real credentials
- JWT tokens expire in 24h (access) and 7 days (refresh)
- OTP codes use `SecureRandom` (cryptographically secure)
- CORS is configured via environment variable — set explicitly for production
- All Gemini API calls go through the backend (API key never exposed to browser)

---

## 📱 Mobile Support

- PWA-ready with `manifest.json` (Add to Home Screen)
- iOS Safari voice recognition supported (webkitSpeechRecognition)
- Urdu Nastaliq font (Noto Nastaliq Urdu) for proper rendering
- Touch-optimized UI with large tap targets

---

## 🗂️ API Reference

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/auth/send-otp` | No | Send OTP to phone |
| POST | `/api/v1/auth/verify-otp` | No | Verify OTP, get JWT |
| POST | `/api/v1/voice/command` | Optional | Process voice command |
| GET | `/api/v1/ai/tip` | No | Get daily AI farming tip |
| GET | `/api/v1/crops/public/list` | No | List all crops |
| GET | `/api/v1/crops/public/{id}/price` | No | Get crop price |
| GET | `/api/v1/marketplace/posts` | Yes | Active marketplace posts |
| GET | `/api/v1/wallet/balance` | Yes | Your wallet balance |
| GET | `/api/v1/wallet/transactions` | Yes | Transaction history |

---

## 🛣️ Roadmap

- [ ] Real SMS OTP (Twilio / Pakistani gateway)
- [ ] Audio file upload for true STT (Google Cloud Speech / Whisper)
- [ ] Push notifications (FCM)
- [ ] Bid/deal lifecycle UI
- [ ] Production deployment (Railway / Render + Vercel)
- [ ] KYC document upload flow
