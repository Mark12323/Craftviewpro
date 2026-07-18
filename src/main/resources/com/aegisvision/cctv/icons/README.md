# CraftView Icon System

Original SVG assets for the CraftView desktop control centre.

## Conventions

- UI icons use a `24 x 24` view box, a consistent `2px` stroke, rounded caps, and `currentColor`.
- Recommended JavaFX display sizes are `16`, `20`, `24`, and `32` pixels.
- Default: `#344054`; hover/selected: `#2563eb`; disabled: `#98a2b3` at reduced opacity.
- Warning: `#d97706`; critical: `#dc2626`; online: `#16a34a`.
- App marks use a `64 x 64` view box. `craftview-app-mark.svg` has a transparent background;
  `craftview-app-tile.svg` includes the blue application tile.

## Asset Groups

- Navigation: `dashboard`, `live-monitor`, `event-log`, `incident-review`, `camera-management`,
  `zone-management`, `ai-settings`, `notification-settings`, `face-registry`, `reports-analytics`,
  `audit-log`, `users-roles`, and `system-health`.
- Camera states: `camera-online`, `camera-offline`, and `camera-impaired`.
- `camera-impaired` also represents a connected feed whose analytics or frame processing is unavailable;
  receiving video alone does not imply an online analytics state.
- Severity: `low-event`, `medium-event`, `high-event`, and `critical-event`.
- Actions: `evidence-snapshot`, `incident-clip`, `acknowledge`, `escalate`, `device-pairing`,
  `restricted-zone`, `processing-load`, `close`, and `search`.

JavaFX does not render SVG files through `Image` directly. Use these masters to generate PNG/ICO bundles
during packaging, or load their path geometry into `SVGPath` controls so colors can follow JavaFX CSS.
