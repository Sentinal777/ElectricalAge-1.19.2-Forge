# Electrical Age 1.19.2 — 0.2.0 experimental port

This pass improves the existing 111 component variants. It does not add the remaining original machines or claim complete Electrical Age parity.

## Original artwork

- Removed automatic mesh normalization. Six-node and machine models use their original coordinate conventions and dimensions.
- Restored missing probe, analog meter, LED, display, trimmer, switch/button and logger model bindings. Corrected passive-component submeshes and omitted exporter reference cubes.
- Restored the original per-item icons, including hyphenated chip names and explicit registration overrides. All 780 migrated PNG files remain byte-for-byte identical to the supplied originals. Mesh vertices, normals and UVs are retained; resource references are normalized for modern Minecraft.
- Cables use the original conductor atlas, junction-cap atlas region and registered widths/depths, including the thermal-cable texture. Arms represent actual connections.
- Restored switch and breaker levers, relay contacts, trimmer knob, button movement, analog-meter needle, LED indicators, four-digit LED atlas, processor rollers/compressor motion and tracking-panel motion. Animation uses the original pivots and angle endpoints where supplied.
- Reused original container artwork in device screens. Removed unrelated editor fields; added battery charge, processing progress and logger history visualization.

## Placement and operation

- Selection/collision envelopes now follow model dimensions and connected cable arms. Aimed-part selection and pick-block work with multiple mounting faces.
- Extending an existing cable along its supporting surface retains the mounting plane. Placement and support-loss handling require a supporting solid face.
- Prevented intersecting bodies on different faces, and wrench rotations that intersect another component. Original floor-only logger restrictions are respected.
- New floor, wall and ceiling placements use an unmirrored orientation frame. Existing 0.1.0 saves retain their original terminal directions; replacing an old component adopts the new convention.
- Signal switches toggle directly. Buttons and changing active states synchronize promptly. Paused processors stop both recipe progress and active animation.
- Tracking panels observe the original +/-45-degree stops; their tilt follows world sunlight independently of pedestal yaw.

## Known limits of this pass

The port remains experimental. The original numeric IDs and native registry names are unchanged. Client and server must both run 0.2.0 because device-menu layouts changed. Legacy 1.7.10 worlds remain unsupported.

Bounding envelopes are conservative boxes, not triangle-perfect collision meshes. Full cable paint/insulation/cover behavior, multipart ghost blocks, original power-terminal connection classes and many placement details remain unported. The digital display currently shows the original default 0–1000 scale for 0–5 V input; its range, dye, dot and strobe controls are not yet restored. Mechanical energy-meter wheels remain static. World logger screens retain their original housing textures; history is graphed in the device menu. Many original sounds, machine inventories, upgrades, thermal mechanics, transformers and progression systems still require work. Held items remain flat icons.

See VALIDATION.md for checks performed on this build and PORT_STATUS.md for the wider feature inventory.
