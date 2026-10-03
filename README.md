# Whiteboard (Android)

Digital classroom whiteboard in Kotlin, with no third-party dependencies (minSdk 26, targetSdk 34).

## Build
Open this folder in Android Studio (Koala or newer), let Gradle sync, then Run.
Or from the command line, with Gradle 8.7+ installed: `gradle assembleDebug`.

## Structure (`app/src/main/java/com/whiteboard/app`)
- `model/`   Board, Stroke, StrokePoint, BoardType, Tool (pure data)
- `data/`    BoardRepository (stroke-based JSON storage), SettingsStore (preferences)
- `drawing/` DrawingView (custom View: bitmap cache + live stroke, undo/redo, stylus pressure)
- `ui/`      Home, SavedBoards, Settings, Board activities, color picker, UI helpers

## Storage format
`filesDir/boards/<id>.json` contains the board id, name, type, background, canvas size,
created/modified timestamps, and every stroke (id, tool, color, opacity, size, points [x, y, pressure]).
`<id>.meta` is a small summary for the gallery and `<id>.png` is the thumbnail only.
Boards are reopened from the strokes, so they stay fully editable.

## Notes
- Google Sign-In is a UI placeholder only ("coming soon"); there is no authentication.
- Stylus pressure varies stroke width when the stylus is used at full opacity; the stylus eraser end switches to the eraser automatically.
