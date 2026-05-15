A personal collection tracker for Android for the Sega Game Gear library. Browse the full game catalog, mark which titles you own by region (Japan, USA, Europe), add notes, and manage cover art.

## Features

**Game List**
- Full Sega Game Gear library loaded from a bundled `games.json` asset
- Alphabetically sorted list with game cover art thumbnails
- Search bar — searches both title and notes fields
- Filter chips: All / Owned / Missing
- Region exclusives filter: cycles through JP Only / US Only / EU Only
- Owned games marked with a checkmark icon
- Configurable list title (saved to SharedPreferences, editable in Settings)

**Game Detail**
- Per-region ownership toggles: Japan, USA, Europe (toggles only appear if the game was released in that region)
- Notes field with debounced auto-save
- Cover art display (tappable to open gallery)
- Editable game title via pencil icon in the top bar

**Images**
- Cover art fetched from IGDB and TGDB APIs
- Tap a game's thumbnail in the list to search for images manually
- Image gallery viewer for a game's available art
- Bulk "Scan Missing Images" in Settings — queries IGDB for any games still lacking cover art
- Disk cache for images (Coil)
- Local file picker to set a custom cover image from your device

**Data / Settings**
- Auto-save on every ownership or notes change (JSON file in external storage)
- Manual "Save Now" — exports current state to a user-chosen location via Android's file picker
- "Load Save File" — restores ownership and notes from a previously exported file
- "Reset All" — clears all ownership toggles and notes (with confirmation dialog)

**Infrastructure**
- Room database (seeded from `games.json` on first launch)
- Legacy cover image URI migration on startup
- IGDB OAuth token management
