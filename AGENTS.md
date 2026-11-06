# NuvioTV guidance

## Go3 / football port

Read `docs/go3-football-port.md` before Go3 work. It records the desktop reference,
TV code owners, test evidence and the verified experimental playback gate plus remaining acceptance checks.

- Android Widevine support and desktop Electron playback do not establish Go3
  playback inside NuvioTV. The user has authorized an experimental non-public-API
  native prototype using normal Go3 device linking and intact Widevine/account
  limits, with no paid service beyond their existing Go3 subscription. Keep its
  unofficial status explicit. Verify native playback and same-surface resizing on
  the designated Sony before the full port. An external app launch does not
  satisfy the video-and-lineups layout.
- Preserve the existing Continue on TV receiver and its stream-selection/player
  hooks when merging upstream changes. In `StreamAutoPlaySelector`, retain
  `TvHandoffReceiver.preferEdition` while allowing upstream binge-group matching
  before the empty filtered-candidate return. Run `StreamAutoPlaySelectorTest`
  and `HandoffDeepLinkTest` after changes to this path.
- Reuse current TV screen, card, overlay, settings and D-pad components. Keep
  Go3/PitchAPI authentication local and separate from the Nuvio session and synced
  preferences. Use existing Keystore-backed storage patterns for secrets.
- Media3's default DRM HTTP factory serves framework provisioning as well as
  license requests. Do not apply a Go3-only license-host rule to provisioning.
  Keep SDK provisioning on an independent credential-free HTTPS transport and
  Go3 account headers on the Go3 license transport; `Go3DrmCallbackTest` checks
  that these requests never cross between the two callbacks.
- Tests must isolate authentication/preferences and use in-memory catalogue
  fixtures. Before intentional real-account catalogue/collection mutations,
  create and verify a durable, restricted recovery backup. Never store credentials
  or browser profiles in source, test reports, logs or APKs.
- Verify the designated device and effective variant/package/signer before
  installation. Preserve installed data, receiver patches and local modifications.
  Do not uninstall, reset data or change signing identity to work around an update.

- Go3 commentary defaults to Lithuanian (`lt`/`lit`) on every fresh playback,
  independently of device/global audio defaults. Ignore saved per-programme audio
  on a fresh Go3 start; preserve explicit manual choices during that session and
  through Lineups resizing. Keep ordinary player language behavior unchanged.
- For offline TV UI tests, use Go3FixtureRunner's plain Application and only the
  named fixture suites; never incidentally initialize auth/sync or run live-account
  suites. TV touch clicks can focus without activating: exercise RequestFocus plus
  actual D-pad Center events. Never capture protected playback or key entry.

- This checkout's full-debug variant can have DEBUG=false/IS_DEBUG_BUILD=true and
  remain non-debuggable. Use IS_DEBUG_BUILD for debug-only presentation; preserve
  the existing debuggable setting. Save generated fixture captures through
  getExternalFilesDir, never enable debugging/capture or use root to retrieve them.
- After the isolated plain-Application instrumentation runner finishes, stop only
  the designated debug package and relaunch it before normal-app acceptance.
  This restores its normal Hilt Application without clearing user data. Establish
  the actual UI/focus after restart before sending a sequence of navigation keys.

- PitchAPI date listings default to played matches. Use status=all for automatic
  discovery of live/upcoming fixtures; never carry returned match status or scores
  into models/UI. Exact matching and confirmed=true still apply.
- Preserve normalized formation coordinates. Never resolve overlap by moving a
  player onto an unrelated grid cell. Size whole markers to the formation-line
  gaps and test actual 4-3-3 versus five-column 4-2-3-1 patterns. Use a list fallback
  for unusable/conflicting coordinates; do not fabricate tactical placements.

- Use surname-only pitch labels, retaining compound particles (for example van Dijk
  and Mac Allister). Measure and fit short surnames on one line. Keep full names
  accessible through player details. Original bench columns use first initial plus
  surname, equal row heights, and original bench counts.
- Match both parsed football teams. Normalize club affixes and controlled acronym
  forms before checking uniqueness. Test that abbreviated names cannot silently
  select a youth, reserve, women's, or repeated fixture. For domestic Go3 matches,
  use the exact programme's COMPETITOR tags and league when available. Rebroadcast
  times do not establish the original fixture date.

- Go3 start-over capability comes from subscribers/products/lives/programmes/{id},
  overlaid onto public programme metadata as the official frontend does. Merely
  adding authentication to the public programme endpoint leaves anonymous flags.
  The official live player also permits start-over when the exact live channel's
  startOver flag is literally true; event channels can enable this while their
  standalone programme flag stays false. Respect these explicit capability paths
  and verify the event epoch inside the licensed seekable DVR window; never use
  arbitrary seekTo(0), change channels or substitute another replay.
- Lineups is available only for explicit sport/sports content. Visible Go3 Refresh
  buttons were removed at the user's request; preserve explicit refresh through
  the remote Options/Menu key and the existing two-minute cache/stale fallback.

- The temporary Go3 on-screen diagnostic counter was removed at the user's request
  for normal viewing. Keep it removed; use bounded, allowlisted internal evidence
  for future playback investigations without exposing URLs or credentials.
