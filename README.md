A personal collection tracker for Android for the Sega Game Gear library. Browse the full game catalog, mark which titles you own by region (Japan, USA, Europe), add notes, and manage cover art. I built this specifically for my Z Fold 6.

## Features

**Game List**
- Full Sega Game Gear library loaded from a bundled `games.json` asset
- Manually reorderable list — long-press any row and drag to reorder; order persists to the database
- While reordering, the list auto-scrolls with an exponential speed curve (slow near center, very fast near the top/bottom visible row); the item continuously swaps with neighbors on every scroll frame even when the finger is stationary, stays pinned under the finger throughout, and is clamped so it never disappears at list boundaries
- Drag handle shown on every row when the list is unfiltered; hidden when search or filters are active
- Search bar — searches both title and notes fields; back button clears the search and scrolls to top
- Permanent clear (✕) button in the search bar
- Filter chips: All / Owned / Missing / Exclusives — evenly spaced across the full row width, flush with the search bar edges
- Region exclusives filter: cycles through JP Only / US Only / EU Only; chip maintains a fixed width so the row never shifts
- Owned games marked with a checkmark icon
- Configurable list title (saved to SharedPreferences, editable in Settings)
- Persistent scrollbar on the right edge — always visible at reduced opacity, full opacity while scrolling; tap and immediately drag anywhere in the right 48 dp to jump directly to any position (scrollbar widens to 14 dp while held)
- Fast-scroll letter indicator — a small translucent letter badge appears at the right edge during high-speed flings or scrollbar drags, showing the first meaningful letter of the topmost visible game (leading "The " is ignored)

**Game Detail**
- Swipe left/right to navigate to the previous or next game in the current list order
- In the wide (tablet/fold inner screen) layout, the list panel scrolls automatically to keep the active game visible as you swipe
- On the narrow (outer/cover) screen, pressing back after swiping to a different game scrolls the list to that game
- Per-region ownership toggles: Japan, USA, Europe (toggles only appear if the game was released in that region); rows are a fixed height so spacing is always consistent regardless of toggle presence
- Notes field with debounced auto-save; keyboard pushes content up so the field stays visible
- Cover art display (tappable to open gallery); image search button pinned to the bottom-right of the cover
- Editable game title via pencil icon in the top bar; title expands to up to three lines

**Images**
- Cover art fetched from IGDB and TGDB APIs
- Tap a game's thumbnail in the list to search for images manually
- Image gallery viewer for a game's available art; image search button in the gallery to find more
- Bulk "Scan Missing Images" in Settings — queries IGDB first (bulk), then falls back to TGDB (per-game) for any still missing
- All scanned images are downloaded to local `file://` storage immediately — no internet connection needed to view them after scanning
- Manually picked images (via the image picker) are also downloaded to local `file://` storage immediately on selection, so they are always included in backups
- On app launch, any images previously stored as remote URLs are automatically migrated to local files in the background
- Local file picker to set a custom cover image from your device
- "Delete All Images" in Settings — removes all cover images from disk and the database (with confirmation dialog); images can be restored with Scan Missing Images

**Data / Settings**
- Auto-save on every ownership or notes change (JSON file in app storage)
- "Backup" — choose to export database only (JSON) or database + all cover images (ZIP); shared via Android's share sheet
- "Load Backup" — restores from a previously exported `.json` or `.zip` backup; ZIP restore extracts cover images and updates all database references automatically
- "Reset All" — clears all ownership toggles and notes (with confirmation dialog)

**Infrastructure**
- Room database v4 (seeded from `games.json` on first launch; `sortOrder` column for manual reordering)
- Legacy cover image URI migration on startup
- IGDB OAuth token management
