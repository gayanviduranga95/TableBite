# TableBite frontend

Vue 3 + Vite + Tailwind CSS 4. See the [main README](../README.md) for setup.

```bash
npm install
npm run dev          # http://localhost:5173 (proxies /api, /uploads, /ws to the Java backend)
npm run dev:https    # same, over HTTPS - needed for live phone-camera QR scanning on the LAN
npm run build        # production build in dist/
```

Proxy targets and API URLs can be changed through `.env.local` (see `.env.example`).
