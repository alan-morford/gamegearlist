A personal collection tracker for Android for the Sega Game Gear library. Browse the full game catalog, mark which titles you own by region (Japan, USA, Europe), add notes, and manage cover art. I built this specifically for my Z Fold 6.

## Features

**Game List**
- Full Sega Game Gear library loaded from a bundled `games.json` asset
- Manually reorderable list — long-press any row and drag to reorder; order persists to the database
- Drag handle shown on every row when the list is unfiltered; hidden when search or filters are active
- Search bar — searches both title and notes fields; back button clears the search and scrolls to top
- Permanent clear (✕) button in the search bar
- Filter chips: All / Owned / Missing
- Region exclusives filter: cycles through JP Only / US Only / EU Only
- Owned games marked with a checkmark icon
- Configurable list title (saved to SharedPreferences, editable in Settings)

**Game Detail**
- Swipe left/right to navigate to the previous or next game in the current list order
- In the wide (tablet/fold inner screen) layout, the list panel scrolls automatically to keep the active game visible as you swipe
- Per-region ownership toggles: Japan, USA, Europe (toggles only appear if the game was released in that region)
- Notes field with debounced auto-save; keyboard pushes content up so the field stays visible
- Cover art display (tappable to open gallery); image search button pinned to the bottom-right of the cover
- Editable game title via pencil icon in the top bar; title expands to up to three lines

**Images**
- Cover art fetched from IGDB and TGDB APIs
- Tap a game's thumbnail in the list to search for images manually
- Image gallery viewer for a game's available art; image search button in the gallery to find more
- Bulk "Scan Missing Images" in Settings — queries IGDB for any games still lacking cover art
- Disk cache for images (Coil)
- Local file picker to set a custom cover image from your device

**Data / Settings**
- Auto-save on every ownership or notes change (JSON file in app storage)
- Manual "Save Now" — exports current state to a user-chosen location via Android's file picker
- "Share Backup" — shares a timestamped JSON backup (`gamegear_backup_DDMMYYYY_HH:MM.json`) via Android's share sheet
- "Load Save File" — restores ownership and notes from a previously exported file
- "Reset All" — clears all ownership toggles and notes (with confirmation dialog)

**Infrastructure**
- Room database v4 (seeded from `games.json` on first launch; `sortOrder` column for manual reordering)
- Legacy cover image URI migration on startup
- IGDB OAuth token management
