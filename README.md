# CNCVerse Clean Extension

A curated CloudStream 3 extension with **12 clean plugins** — no ads, no popups, no web redirects.

## Plugins

| Plugin | Content | Language |
|--------|---------|----------|
| CNC Verse | Netflix · Prime · Hotstar · SonyLiv · JioCinema · ZEE5 | Hindi |
| MovieBoxProviderIN | Movies · TV Series | Hindi |
| CastleTvProvider | Movies · TV Series | English |
| PikashowProvider | Movies · Web Series · Bollywood · South Indian | Hindi |
| StreamFlixProvider | Movies · TV Series · Anime | English |
| Watch32 | Movies · TV Series | English |
| XonProvider | Movies · TV Series · Anime | English |
| Rtally | Movies · Series · Anime · Asian Drama | English |
| HDrezkaProvider | Movies · Series · Anime · Asian Drama | English |
| EinthusanProvider | Tamil · Telugu · Hindi · Malayalam Movies | Tamil |
| CricifyProvider | Live Cricket · Sports | English |
| SKTechProvider | Indian Live TV Channels | Hindi |

## What was removed vs original

- ❌ **23 plugins removed** (duplicates, broken, niche regional)
- ✅ **No ads** — all `loadLinks()` functions filter ad/redirect/tracking URLs
- ✅ **No popups** — ad iframe patterns blocked at source
- ✅ **No web redirects** — `isAdUrl()` guard on every link resolver
- ✅ **No consent dialogs** — no AlertDialog or terms popups in any plugin

## Add to CloudStream

1. Fork this repo on GitHub
2. Create a `builds` branch
3. Push to `master` — GitHub Actions builds and deploys automatically
4. In CloudStream: **Extensions → Add Repository** → paste your `builds/repo.json` URL

## Build locally

```bash
./gradlew make makePluginsJson
```
