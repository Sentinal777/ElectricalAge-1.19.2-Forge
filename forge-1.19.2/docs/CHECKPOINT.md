# Release checkpoint: 1.19.2-0.4.0-port

The complete modified source, original resources and licenses, exact 1.24.6 reference, wrappers, generated data, source-level audits and validation tools are preserved in the source deliverable. Previous recovery artifacts and the 0.3 preview are retained separately. Work is not left only in a temporary runtime folder.

Current equipment families and explicit verification limits are in PORT_STATUS.md and feature-coverage.json/csv. All required tests in the recorded full/native profiles passed; normal production profiles include actual world restarts and a 256-cable loaded mesh. No known compile blocker remains on the pinned target. First build needs dependencies; cached builds can use --offline.

Further maintenance should retain stable native/legacy IDs, use the exact source baseline, preserve finite physical state through save/pickup/chunk lifecycle, and validate the normal JAR after any API change. Additional manual survival/multiplayer/modpack campaigns, unexercised installation variants, legacy optional integration replacements and localization/configuration parity remain useful verification or compatibility work; no missing major active baseline equipment family is hidden behind registration counts.

The historical 0.3 audit is retained under docs/history and must not overwrite current results. tools/audit_coverage.py now delegates to pinned-baseline reconciliation. Per-device behavior audits and executed logs remain separate from registration discovery. No future scheduled work is created by this release.
