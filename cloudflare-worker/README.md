# triplo.club — Cloudflare Worker

Platform-detecting redirect for the Triplo landing domain:

- **iOS** visitors → the App Store listing
- **Android** visitors → the Google Play listing
- **Desktop / other** → a small landing page with both store links
- `/privacy` → the privacy policy (use `https://triplo.club/privacy` for the store listings)

## Deploy

Requires Node.js. From this directory:

```bash
npx wrangler login     # opens a browser to authorize the Cloudflare account
npx wrangler deploy
```

`wrangler` reads `wrangler.toml`, which attaches the Worker to `triplo.club`.

## Before / after launch — things to update in `src/worker.js`

- `APP_STORE_URL` — replace `<APP_STORE_ID>` with the numeric App Store ID once the iOS app
  is created in App Store Connect. Until then, iOS visitors see the landing page (which shows
  "Coming soon to iOS") instead of being redirected.
- `PLAY_STORE_URL` is already final — it is derived from the package name
  `com.allmeatgames.triplo` and resolves as soon as the app is published.
- `PRIVACY_UPDATED` / the privacy policy text — update if the app's data practices change.
