# Kling Motion Control — Android personal starter

This project contains:
- `android/`: native Android app (Kotlin + Jetpack Compose)
- `backend/`: Node.js API proxy. Keeps the Kling API key off the phone and accepts image/video uploads.

## Important
This is a starter project, not a signed APK. Build it with Android Studio. Kling API usage may incur charges. Confirm your account has API access and sufficient balance before generating. Never put the Kling key in the Android app.

Official Kling Motion Control 3.0 docs: https://kling.ai/document-api/api/video/motion-control

## 1. Backend setup
Requires Node.js 20+ and a publicly reachable server with persistent disk storage. The server must be reachable by the Android phone and by Kling (for the uploaded media URLs).

```bash
cd backend
npm install
cp .env.example .env
```
Edit `.env`:
- `KLING_API_KEY`: your Kling API key
- `PUBLIC_BASE_URL`: public HTTPS base URL of this backend, e.g. `https://your-domain.example`
- `PORT`: usually 3000

Start:
```bash
npm start
```
Test `https://your-domain.example/health` and ensure uploaded files are publicly accessible via `/uploads/...`.

For local development, a localhost URL on your phone is not enough for Kling to fetch the files. Use a public HTTPS host/tunnel. Do not expose an unprotected production server; add authentication/rate limiting before public use. This starter limits upload size and validates file extensions, but is intended for personal use.

## 2. Android setup
1. Open the `android` folder in Android Studio (recent stable version).
2. Wait for Gradle sync.
3. Open `android/app/build.gradle.kts` and set `BuildConfig.API_BASE_URL` to your backend URL, ending in `/`, for example `https://your-domain.example/`.
4. Run on an Android 8.0+ device.

The app lets you select an image and a video, upload them to your backend, submit Motion Control, check task status, preview the generated video and download it. The task history is stored locally on the phone.

## Kling Motion Control constraints
Based on the official docs at the time this starter was prepared:
- Image: JPG/JPEG/PNG, up to 50 MB, minimum 300 px on each side, aspect ratio 1:2.5 to 2.5:1.
- Video: MP4/MOV, up to 100 MB, at least 3 seconds. Maximum duration depends on orientation: up to 10 seconds for `image`, up to 30 seconds for `video`.
- Video dimensions: 340–3850 px.
- `character_orientation`: `image` or `video`; `video` follows the reference video's orientation and allows up to 30 sec.
- Output resolution: `720p` or `1080p`; audio: `original` or `off`.

The backend currently defaults to `character_orientation=video`, `resolution=1080p`, and `audio=original`. Adjust the form/backend if you want different defaults.

## 3. API routes
- `GET /health`
- `POST /api/upload` multipart field `file` -> `{ url, filename }`
- `POST /api/motion` JSON `{ imageUrl, videoUrl, prompt?, characterOrientation?, resolution?, audio? }`
- `GET /api/tasks/:taskId` -> Kling task response

Kling create endpoint: `POST https://api-singapore.klingai.com/motion-control/kling-3.0`
Kling query endpoint: `GET https://api-singapore.klingai.com/tasks?task_ids=...`

## 4. Troubleshooting
- `401/403`: check the API key, account API access, and authorization permissions.
- Kling rejects media URL: ensure the URL is HTTPS, public, direct-downloadable, and does not require cookies/login.
- Task fails: inspect the `message` returned by Kling; check image/video requirements and content policy.
- Download URL expiry: Kling result URLs are retained for 30 days per docs. Download the result promptly.
- Android cannot connect: check `API_BASE_URL`, HTTPS certificate, firewall, and server reachability.
