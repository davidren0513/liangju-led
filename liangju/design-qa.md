# Design QA — 亮句 Android

final result: passed

## Evidence and target

- Source visual truth: `design/source-option-1.png` (853 × 1844 px), first displayed generated option selected by the user.
- Final implementation: `design/implementation-android-final.jpg` (427 × 952 px).
- In-app browser viewport: 1400 × 1200 CSS px. Android screen measured at exactly 427 × 952 CSS px, scale 1; screenshot density normalized to 1 output pixel per CSS pixel.
- User explicitly requested Android. The selected unframed mobile source was adapted to the protected Pixel 10 runtime. Android status/camera top area and 48 px navigation region are expected additions, not app design drift.
- Source normalized to 427 × 924 px for the paired comparison, preserving its aspect ratio. Implementation remains 427 × 952, preserving Android geometry. App regions were compared separately from device chrome; no stretched source or whole-stage fidelity claim.
- State: initial smart mode, original borrowing-passage example, fluorescent yellow, left direction, medium speed, paused preview.
- Full-view comparison: `design/comparison-final.jpg`, opened with both source and implementation in one image.
- Focused LED and result/control comparison: `design/comparison-detail.jpg`, opened separately at readable size.
- Additional states: `design/voice-tested.jpg`, `design/edit-keyboard-final.jpg`, `design/fullscreen-final.jpg`, `design/plain-text-tested.jpg`.

## Comparison history

### Iteration 1

Evidence: `design/comparison-initial.jpg` and `design/implementation-android-initial.jpg`.

- [P2] Heading, result and brand were smaller than the selected reference. Increased the title to 37 px, brand to 26 px and editable result to 30 px. Improved LED text measurement and dot sampling.
- [P2] Footer could overlap the appearance row by about 1.5 px. Reduced heading/section spacing and removed excessive CTA vertical padding.

### Iteration 2

- [P1] Dismissing the keyboard could leave the outer phone container programmatically scrolled by 85.5 px, cropping device/header content and exposing the hidden keyboard. Added an app-scoped `overflow: clip` rule to the existing screen; protected runtime files remain unchanged and inner MobileScroll/sheet scrolling remains intact.
- Repeated the complete manual-input flow after the fix. Screen and app viewport top positions both measured y=124; outer scrollTop=0; keyboard visible=false. Confirmed entered text on the home screen. Evidence: `design/edit-keyboard-final.jpg` and final home capture.

### Final comparison

- No outstanding P0/P1/P2 findings.
- Appearance row and action footer have a measured 13.96 px clear gap; footer remains above Android navigation.
- No cropped controls, missing text or visible keyboard remnants in the final capture.

## Required fidelity surfaces

- **Fonts/typography:** Chinese system sans family, bold title and result, muted secondary text, and compact rounded controls match the reference hierarchy. Native font rendering differs slightly from the generated image; no text rasterization is used for editable controls.
- **Spacing/layout:** preserves header → headline → LED preview → original/result comparison → mode selector → appearance → voice/display actions. Footer is fixed for reachability. Android safe-area additions account for the different canvas proportions.
- **Colors/tokens:** near-black #0d100c, charcoal surfaces, fluorescent #d9ff56, gray-green secondary text. LED color changes independently from brand/control emphasis.
- **Image/assets:** the design contains no illustration or photography requiring new raster assets. The selected source is retained locally. Actual LED display is generated from editable text using Canvas; Phosphor icons provide consistent line icons. Protected device and keyboard assets remain intact. This is functional display rendering, not replacement of a static decorative asset.
- **Copy/content:** original phrase and concise result retained. “本地处理” was intentionally changed to “交互演示”; voice controls and sample panels disclose simulated processing so the prototype does not claim a loaded AI model.

## Interaction validation

Performed in the in-app browser, not inferred solely from build output:

- Color picker: sky-blue selected and applied.
- Direction choices: up, right, down and still selections verified; left is default. Fullscreen direction updates.
- Speed range: keyboard End changes medium/3 to very fast/5.
- Font settings: clear text mode and 150% size applied; full-screen rendering inspected.
- Original/smart switch updates the displayed message.
- Voice demo: hold/release opens sample sheet; cheer example progresses to a disclosed preset result; confirm applies “小林加油！” with pink/up settings.
- Text editing: empty text disables confirm; “别在东门等，来西门” is preserved exactly; Android keyboard does not cover confirmation; manual entry selects original mode.
- Display flow: opens phone-scoped full display, focuses return control, hides editing controls from accessibility tree, supports pause/continue and horizontal presentation, returns via button or Escape.
- Browser console checked after clean reload and interactions: no new warnings/errors. The initial dependency installation produced transient Vite React cache errors; those did not recur after reload.
- Production build and TypeScript compile passed. `check:runtime` passed with all 28 protected files unchanged.

## Expected limitations / P3 follow-up

- LED dots have slightly different glyph sampling and glow than the generated concept; they remain functional, legible and editable.
- Horizontal presentation rotates content within the prototype, not the physical device or browser orientation.
- No APK, speech engine, language model, persistence, microphone permission, brightness API, native back navigation or true on-device performance testing is included in this interface-only deliverable.
- System chrome and keyboard are simulation assets, not a running Android emulator.

## Implementation checklist

- [x] Preserve selected visual direction and Android device runtime.
- [x] Resolve typography and footer overlap.
- [x] Fix and retest keyboard dismissal offset.
- [x] Capture and inspect combined final comparisons.
- [x] Test primary interactions and inspect console.
- [x] Pass build and runtime integrity check.

## Continuous scrolling update — 2026-11-01

- Replaced the viewport-sized repeat interval with tiled message copies separated by approximately two character widths (capped below half the scrolling viewport for very small previews).
- Applies to horizontal and vertical directions, portrait/rotated display and preview. Pause keeps the tiled positions; untouched static previews and still mode retain one centered message.
- In-app browser checked horizontal preview and vertical presentation; multiple copies can coexist on-screen. Restored leftward animated preview for handoff. No browser errors or warnings.
- Production build and the 28-file runtime integrity check passed.
- Visual theme/layout unchanged. final result: passed.

## About and publication update — 2026-11-01

- Settings ends with an About row before its completion button.
- About displays author davidren0513, version 0.1.0, project description, prototype limitations and the repository URL.
- In-app browser verified Settings → About → Settings; font/style state is preserved. Screenshot: `design/about.jpg`.
- Production build and protected runtime check passed. Vite reports a non-blocking 506 kB main-bundle size advisory; no compile errors.
- final result: passed.
