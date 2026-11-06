# Go3 and spoiler-free football lineups on Android TV

Recorded 2026-11-04. Target: NuvioMedia/NuvioTV, Sony BRAVIA 4K AE2,
Android 14, armeabi-v7a. This is an experimental native integration using Go3's
non-public API, explicitly authorized by the user. It is not an endorsed Go3 SDK.
No additional paid service is used beyond the user's existing subscription.

## Current verification boundary

**The experimental native playback and same-surface resize gate passed.**
Go3 Sport 3 played inside NuvioTV through Media3 and ordinary Widevine. Across
four complete resize/restore cycles, player identity remained `c44fe2f`, source
loads 1, DRM key loads 1 and first-frame events 1. Decoded frames advanced from
133 to 7,944 and position from 14,374 to 14,675 seconds. One server video-session
renewal succeeded. The user physically confirmed visible video and audible sound,
uninterrupted through size changes. Capture protection remained intact. No
protected screenshots or license payloads were inspected.

The normal catalogue → detail → Watch route has subsequently passed native live
Fenerbahçe/Liverpool and exact replay playback, with Lithuanian commentary
physically confirmed. R10 real PitchAPI lineups, formation placement, remote
bench scrolling, nested Back and same-player cache/restore passed. R13 passed
123 JVM and ten Sony fixture tests covering the latest requested controls and
name layout. See the current review manifest and final evidence below for APK
identity and start-over acceptance. Account-token expiry and DRM-license expiry/
renewal remain unverified; server session prolongation does not prove either.

The earlier official external Go3 app test was Barcelona vs Aston Villa from
2026-11-03, reported working by the user. Keep this separate from native evidence.
No live sports programme was available during that earlier baseline. The native
Sport 3 channel test above subsequently established in-app licensed playback.

## Discovery and exact destinations

Search → Discover → Sports uses the existing selector, TV cards, readiness and
focus handling. Built-in sources: Go3 Live Events (`live`), Go3 Premier League
(`epl`), Go3 Champions League (`ucl`), Go3 Europa League (`uel`), and Go3
Conference League (`uecl`), Go3 La Liga (`laliga`), Go3 Bundesliga (`bundesliga`),
Go3 Ligue 1 (`ligue1`), Go3 Serie A (`seriea`), and Go3 FA Cup (`facup`).
Official source URLs are in
`Go3CatalogueSource`; programme identity is `go3:programme:{programmeId}`.

Read-only credential-free native metadata returned 5 live-section entries,
20 EPL entries and 18 UCL entries during investigation. Endpoints:

- `products/sections/7998051`, nested `elements[].item` PROGRAMME cards.
- `products/lives/programmes/catalog` filtered by `categoryId[]` 6886315 / 6886205 /
  6886318 / 6886320 / 8272112 / 7934060 / 7498236 / 7495922 / 6886370,
  paginated with firstResult/maxResults and bounded response/page limits.
- `products/lives/programmes/{programmeId}` for fresh details.

Only programme cards cross the decoder. Footer/linear-channel objects are not
programme entries. Artwork comes from images.16x9 mainUrl/miniUrl, allowing
scheme-relative static3.go3.tv/scale/go3 URLs and safe transformation parameters.
Deduplication retains earlier artwork when a later lazy entry lacks it. Public
`allowPlayback=false` is not a reliable entitlement check for an authenticated
subscriber. Actual playback keeps Go3's entitlement/region/concurrency checks.

Each source has a two-minute in-memory cache, explicit refresh through the remote Options/Menu key, dynamic
Live/Upcoming/Replay choices and a marked stale fallback after refresh failure.
An upcoming entry never enables Watch. The detail refresh completes before Watch
is enabled. Exact canonical programme URLs are preserved, including legitimate
`/lt/` paths. Programme ID, live-channel ID and recording ID remain distinct.
Replay uses `programRecordingId` with CATCHUP, never the live channel. UCL can use
virtual Extra channels, explaining the prototype's incomplete Sport 3 EPG lookup.

## Native playback and local accounts

Normal Watch routes contain only a public programme ID, title and public art.
Fresh metadata and the private playback lease are resolved inside the existing
player controller. Signed manifests, license URLs and account headers never
enter navigation/SavedState stream URLs. Go3 forces Media3, skips incompatible
engine failover, and uses the existing player controls, tracks and lifecycle.
Sports programme IDs do not enter the synced movie/series collection or scrobble
contract; existing Continue on TV behavior for ordinary content is retained.

Go3 API/license transport rejects redirects. A separate credential-free media
client permits ordinary same-scheme CDN redirects. Widevine SDK provisioning
uses its own credential-free HTTPS callback. Go3 account headers are confined
to the separately guarded Go3 license callback. Do not apply the Go3 license-host
restriction to SDK provisioning: R2 demonstrated DRM_PROVISIONING_FAILED until
these standard callbacks were separated. No DRM keys, security-level downgrade,
profile transplantation or capture-protection change is involved.

Settings → Content & Discovery → Go3 offers private device linking, viewing
profile selection and confirmed local-only reset. Tokens use the existing
Keystore-backed ProtectedProfilePreferences in a dedicated Go3 slot. The
prototype's authorized local session is reused, without extracting it into tools.
Account/entitlement errors are fixed neutral strings. Provider bodies, signed
request URLs and nested errors are excluded from player error/load diagnostics.
Media3 URL logging is suppressed for the private lease lifetime.

## Lineups and PitchAPI

The existing playback overlay has a labeled Lineups action. Opening changes the
same surface's bounds: approximately 38% of safe available width at top right,
with the coach/original bench rail at the same width below it. A 105:68 horizontal
pitch is on the left. Its centre circle is drawn with a single physical radius.
Compact markers are positioned as units; the away side rotates consistently.
Safe coordinates are finite numeric values in [0,1]. Marker sizing adapts to formation-line spacing; players are never moved to an
arbitrary grid. Missing or conflicting coordinates use the original-starter list. Player details
expose full names. Surnames appear on the pitch. Header Restore video and Player
controls remain accessible while the content scrolls independently.

PitchAPI uses https://api.pitchapi.dev/v1 with X-API-KEY, Accept application/json
and User-Agent NuvioTV/1.0. Fixtures use /date/YYYY-MM-DD and lineups use
/matches/{opaqueStringId}/lineups. Data crosses only explicit match/team/player
allowlists. Only a literal boolean confirmed=true admits a side. Scores, status,
ratings, events, goals, cards, substitution/participation indicators, raw provider
errors and provider links never enter presentation models. Original bench order
is retained. No provider match page is embedded. Optional artwork is omitted.

Configure the key privately in Settings → Content & Discovery → Football Lineups.
It is masked, validated with a date request, removable, Keystore encrypted and
outside synced preferences. Never send it in chat, source, fixtures, APK inputs,
logs or screenshots. No desktop credentials or browser sessions are migrated.

Opening Lineups resolves both teams from programme/playback metadata. Normalize
case, accents, punctuation and Manchester United aliases. An explicit ISO
original date takes precedence; otherwise search today in UTC and the preceding
fourteen dates. Exactly one matching fixture is required. Repeated fixtures are
ambiguous, even if one is newer. Neutral unavailable states offer Retry. There
is no manual team/date form. Successful sanitized results are cached for that
playback session only; closing, selection/profile changes and cancellation
invalidate late responses. No match-event polling occurs.

## Build and test workflow

Existing application ID/signing configuration and receiver patches are retained.
The user approved in-place prototype installation over `com.nuviodebug.com`,
beta.10 (1073), matching SHA-256 signer
`bb768762dd4066291bcc4ea735c526bf1b67c46eb82ce35125976bcdb588b42b`.
Never uninstall, change signer or reset data to install an update. Separate release
`com.nuvio.tv` and official Go3 `lt.go3.android.tv` remain untouched.

A verified restricted copy of the original APK and recovery.json is in
`../.local-app-backups/go3-prototype-20261104/`. This is a binary recovery backup,
not an account-data backup. No intentional real-account catalogue/collection
mutation was performed. Use fixtures and isolated authentication for tests.

R3 accepted prototype:
`build/go3-prototype/nuvio-go3-native-prototype-1073-r3-armeabi-v7a.apk`, SHA-256
`79cc07de31da37c340cb57dbc764917fe83c5c6049dc69b8a954e752946f334a`.
R4 integrated review APK installed in place successfully. The three Go3 choices
were verified through the real Sony Sports selector. Live Events loaded five Live
cards. UCL loaded Upcoming/Replay choices, with unavailable Live absent. Selecting
Replay rendered the exact Manchester United/Roma and Barcelona/Aston Villa cards.
The normal card → detail → Watch handler started Manchester United/Roma recording
13370316 (programme 12716196). The user physically confirmed picture and audio.
The normal Lineups action opened and focused Restore video; Back restored video,
then exited to the exact programme detail. PitchAPI was not configured yet.

R4 initially followed the device/default Estonian audio. The user requested
Lithuanian for every Go3 start. R5 forces Lithuanian language preference only for
Go3 and ignores saved audio choices on a new Go3 playback start, while preserving
manual in-session selections and same-player presentation changes. Physical
language/resize verification of R5 is pending. Card labels remove the competition
prefix to keep team names visible; original titles remain intact in metadata.

Build with the existing local signing override:

```sh
./gradlew -I build/tv-install/debug-signing.gradle :app:assembleFullDebug
```

Offline JVM checks use in-memory catalogues and fake HTTP transports. TV fixture
screen tests must use the dedicated plain Application runner to avoid starting
Nuvio authentication/sync or touching user preferences:

```sh
./gradlew -I build/tv-install/debug-signing.gradle \
  -PnuvioFixtureRunner=com.nuvio.tv.go3.Go3FixtureRunner \
  :app:assembleFullDebugAndroidTest
```

Run only Go3TvScreensTest and FootballTvLayoutTest with this runner. Never run the
real-account instrumentation suites incidentally. After this plain-Application
runner finishes, stop only the designated debug package and relaunch it before
normal-app acceptance; this restores the Hilt Application without resetting data.
The variant deliberately has DEBUG=false, IS_DEBUG_BUILD=true and is non-debuggable.
Preserve that setting. Generated unprotected fixture captures use getExternalFilesDir;
do not enable debugging or weaken protection to retrieve private cache files. Fixture captures are permitted
only for unprotected generated team sheets, never for licensed Go3 video or key
entry. Physical protected video acceptance uses viewing/listening and sanitized
decoder counters. Do not disable FLAG_SECURE for proof.

The separately authorized dev→develop merge is local `54b22c4c5` (origin/dev
`99fbd65d8`, 170 upstream commits). The single StreamAutoPlaySelector conflict
retained TvHandoffReceiver.preferEdition and upstream's deferred empty-candidate
check. Forty-one focused receiver/selection/player checks passed after the merge.
Nothing was pushed. No subsequent feature source was committed.

## Desktop inventory

| Concern | Reference and behavior to preserve |
| --- | --- |
| Sources and cache | `Go3Catalog.kt`: `live`, `epl`, `ucl`; two-minute in-memory cache; explicit refresh; stale fallback; filters from loaded availability; stable known programme identity |
| Public metadata | `scripts/go3-live-probe/events.cjs`, `event-catalog.cjs`: official source URLs, programme-only cards, exact URLs and IDs, artwork host/query allowlist, label normalization, upcoming precedence, carousel traversal and preservation of previously collected artwork |
| Details | `Go3EventScreen.kt`: shared detail backdrop/actions, exact replay destination, disabled upcoming Watch, Back and focus ownership |
| Account actions | `Go3SettingsPage.kt`: official sign-in/open-account flow, confirmed Go3-only session reset, no Nuvio sign-out |
| Platform playback | `Go3Playback.kt`, desktop `Go3PlayerLauncher.kt`, `Go3RuntimePaths.kt`, helper `main.cjs`: official browser player, private local session, serialized catalogue requests, bounded requests, sanitized diagnostics; Electron/process/profile infrastructure is not portable to TV |
| Provider boundary | Common `FootballLineups.kt`, desktop `FootballLineups.desktop.kt`, helper `lineups.cjs`: PitchAPI, opaque match IDs, allowlisted models, confirmed sides only, finite normalized coordinates, original starters/bench/coaches, neutral errors |
| Matching and lifetime | Automatic both-team matching; accent/punctuation/case and Manchester United aliases; original explicit date or today plus previous fourteen dates; exactly one match; neutral Retry; successful session-only cache; dismissal invalidates responses |
| Presentation | Native `FootballLineupsPanel.kt` and helper `installLineups`: existing video remains mounted; 105:68 horizontal pitch; square centre circle; compact markers; approximately 38% video/bench rail; vertical scrolling. Adapt to TV components and physical remote, not browser pixels |
| Key configuration | Desktop local settings and provider: separate local PitchAPI slot, masked validation/removal; replace desktop User-Agent with `NuvioTV/1.0`; no synced secret or embedded key |

Authoritative behavior docs: `../NuvioDesktop/docs/go3-live-probe.md` and
`../NuvioDesktop/docs/football-lineups.md`. Earlier `catalog.cjs` channel discovery
is not the programme catalogue: `event-catalog.cjs` is the relevant source.
Never route a replay through `safeChannel` or replace its URL with its channel.

## TV owners and existing references

Paths below are relative to `app/src/main/java/com/nuvio/tv/`.

| Area | Existing owner/reference |
| --- | --- |
| Discovery and source selector | `ui/screens/search/DiscoverScreen.kt`, `SearchDiscoverSection.kt`, `SearchViewModel.kt`, `SearchUiState.kt`, `SearchEvent.kt`; reuse actual selector, grid/card, focus restoration and loading behavior |
| Details and Watch | `ui/screens/detail/MetaDetailsScreen.kt`, `MetaDetailsViewModel.kt`, `HeroSection.kt`, navigation `NuvioNavHost.kt` and `DetailChildHost.kt` |
| Settings | `ui/screens/settings/SettingsScreen.kt`, `ContentDiscoverySettingsContent.kt`, `PlaybackPlayerSettings.kt`, `SettingsDesignSystem.kt`; use the current nested Back/focus behavior |
| Video and overlay | `ui/screens/player/PlayerScreen.kt` owns Android video view integration and private `PlayerControlsOverlay`; `PlayerViewModel.kt`, `PlayerRuntimeController*.kt` own lifecycle/state; `PlayerMediaSourceFactory.kt` builds media sources |
| Nuvio authentication | `core/auth/AuthManager.kt`, `SupabaseAuthSession.kt`, `ui/screens/account/`; do not reuse Nuvio sign-out for Go3 |
| Local secret storage | `core/profile/ProtectedProfilePreferences.kt`: Android Keystore AES/GCM with profile-bound AAD; `data/mdblist/AndroidMdbListAuthPersistence.kt` is an existing consumer; also inspect `data/simkl/AndroidSimklAuthStorage.kt` |
| Continue on TV | `core/handoff/TvHandoffReceiver.kt`, `MainActivity.kt`, `core/deeplink/HandoffDeepLink.kt`, `StreamAutoPlaySelector.kt`, `ui/screens/stream/StreamScreenViewModel.kt`, and player initialization/observer/lifecycle hooks; retain edition selection, consent and resume behavior |

No target `AGENTS.md` or target skill file was present during initial inspection.
Desktop instructions were read as reference context, not as a requirement to
launch desktop playback during this TV investigation.

Current build configuration has `full` and `playstore` flavors. Debug IDs are
overridden in `androidComponents`: full debug is `com.nuviodebug.com`, Play Store
debug is `com.nuvio.appdebug`; release defaults differ. Inspect the effective
variant, existing signer and installed package before any separately authorized
installation. Do not infer signing identity from the app label.


## Remaining physical acceptance

Use only the designated Sony and the agreed full-debug variant. Verify the
normal Sports selectors and exact details, one native live programme and one
native exact replay, pause/resume, supported seeking, session errors/renewal,
repeated Lineups cycles while playing and paused, selected tracks/volume,
remote scrolling/Back/restore, safe areas and reading distance. Ask the user to
configure PitchAPI privately on the target app when the UI is ready. Recheck
Manchester United/Roma 2026-11-03 retention before attempting that lookup. Separate
public metadata, fixture UI and physically verified playback evidence in delivery.


## Integrated review candidate: R9

Installed in place on the designated Sony with the same signer and app data.
APK: `build/go3-review/nuvio-go3-lineups-1073-r9-armeabi-v7a.apk`.
SHA-256: `859dca376725be619a99a3383a749b03096764c42cd69d112cd8dac53b9746bc`.
`build/go3-review/review.json` records the exact source hashes and acceptance state.
Source changes remain uncommitted. Signing, application ID and debuggable policy
were preserved. Ordinary Continue on TV selection/deep-link tests passed.

116 focused JVM checks pass, zero failures/errors/skips: Go3 linking/API/DRM
transport separation, programme identity/availability, pagination/artwork/footer
filtering, two-minute/stale cache, real SearchViewModel source/filter/refresh
handlers, Lithuanian language defaults, PitchAPI headers/opaque routes,
spoiler-field exclusion, literal confirmation, finite coordinates, aliases,
original dates/date boundaries/ambiguity, cancellation/cache reuse, collision
layout at TV sizes, and existing search/player/receiver regressions.

The Sony fixture suite covers six tests with isolated Application/authentication:
actual catalogue D-pad activation, exact replay detail Watch, disabled Upcoming,
confirmed local-only reset with Back cancellation, full 22-player layout with
accessible full names/restore/player controls, and 960/1280/1920 logical TV
viewports. A rendered-image review caught low-contrast header/coach text; R9 owns
its foreground colors and adds a pixel-contrast assertion. Current suite outcome
is recorded in the review manifest after the final run. These are generated
fixtures, not live PitchAPI results or protected-video screenshots.

The user physically confirmed normal native Manchester United/Roma picture/audio
and subsequently confirmed Lithuanian commentary after a fresh Go3 start.
All fresh Go3 live/replay sessions prefer Lithuanian independently of global or
device audio preferences; explicit in-session choices remain possible. R9 includes
the same audio rule plus debug-only safe player/source/key/frame/position/language
counters. Repeated Lineups changes must retain player/source/key counts and pause.

### Setup and remaining user acceptance

1. Open the existing Discover selector → Sports. If Discover is configured inside
   Search, use Search's Discover button. Choose Go3 Live Events, Go3 Premier League
   or Go3 Champions League. Choose only the availability filters offered.
2. Open a programme and select Watch. Upcoming programmes cannot play. The retained
   Manchester United/Roma replay is programme 12716196 / recording 13370316;
   Barcelona/Aston Villa is programme 12689361 / recording 13343769, listed through
   November 10 at the time of recheck. Retention can change.
3. Existing Go3 linking is retained. To link/change viewing profile, use Settings →
   Content & Discovery → Go3 and complete approval privately. Reset is local-only
   and requires confirmation; it does not sign out Nuvio or the official Go3 app.
4. Enter an existing/free PitchAPI key privately in Settings → Content & Discovery
   → Football Lineups. Use Validate and save. No key is requested in chat, read
   from desktop storage, embedded in a build, logged or synced. No paid service
   beyond the existing Go3 subscription is authorized or required by this build.
5. During playback open the normal overlay (Up) and select Lineups. Restore video
   stays in the header. Player controls reopens the existing playback controls.
   Back dismisses nested controls, then Lineups, then follows ordinary playback.
   Reopening reuses successful sanitized session data.
6. Verify real confirmed XI/bench/coach data, viewing-distance readability,
   scrolling, physical-remote Back/restore, and uninterrupted licensed picture/audio
   through repeated open/close cycles while playing and paused. PitchAPI live
   acceptance remains pending private key configuration. Account-token and
   DRM-license expiry recovery remain unverified; the server session renewal
   already observed does not prove either. The integration remains unofficial.


### Final R9 evidence

All 116 JVM checks and six Sony fixture UI tests passed with no failures. The UI
suite includes visible/accessibility spoiler exclusion and actual rendered
header/coach foreground contrast. The unprotected fixture capture is
`build/go3-review/sony-lineups-fixture.png`; it contains no licensed video.

Normal native Manchester United/Roma R9 diagnostics: player `f135fd4`, source
loads 1 and DRM key loads 1. Opening Lineups advanced decoded frames 62→862 and
position 1→17 seconds. Three further restore/open cycles reached 3,367 frames and
67 seconds, still the same player/source/keys and Lithuanian (`lt`) audio. While
paused, compact and restored presentations both held 5,044 frames / 100 seconds,
paused=true, audio lt. Replay fast-forward advanced to 112 seconds, rewind returned
to 100, both paused and without source/DRM reload. Resuming advanced to 5,164 frames
/ 102 seconds, paused=false, still the same player/source/keys/audio. Initial
pause-overlay dismissal is normal existing Back/media-control behavior.

The real Go3 lease was then closed through Back and the app returned to its exact
replay catalogue. Settings → Content & Discovery → Football Lineups was reached
through actual remote navigation. The masked key field is focused for private
user entry. No UI dumps, screenshots, typing or TV control should run during
private entry. Await the user's confirmation before live provider acceptance.


### Live-event and formation correction (R10)

The user reported distorted tactical placement and requested the live
Fenerbahçe/Liverpool event, programme 12716199, rather than older replays.
Native licensed live playback was opened through the actual UCL Live filter;
sanitized counters showed decoded frames advancing, Lithuanian audio, one source
and one DRM key load. Do not substitute another programme for this acceptance.

The [official PitchAPI reference](https://pitchapi.dev/) explains that date listings
are played-only by default. Request `/date/{date}?status=all` to include live and
scheduled fixtures while continuing to discard result/status fields at decoding.
A sanitized read-only check of November 4 first returned four unrelated fixtures
with the old default; with status=all it returned exactly one Fenerbahçe/Liverpool
match. Both sides were confirmed, with eleven starters and eleven original bench
players each; formations were 4-3-3 and 4-2-3-1. No scores/events/raw responses were
emitted or retained. The account key remained private and outside source/builds.

The previous collision solver was wrong: fitting a five-column formation into a
half pitch with fixed 56dp markers moved players to unrelated grid cells. R10
keeps the original normalized X/Y coordinates and consistent away rotation,
scales complete markers to the spacing between formation lines, and uses list
fallbacks for missing/conflicting data. Tests assert exact coordinate projections
and line counts for the requested live event's sanitized coordinate patterns,
instead of accepting arbitrary non-overlapping positions. Real-event formations
also replace the simpler symmetric fixture in the TV render suite. Physical
acceptance of the corrected build is pending its install/run.


### R10 installed verification

Review APK: `build/go3-review/nuvio-go3-lineups-1073-r10-armeabi-v7a.apk`.
SHA-256: `4073040806d1c383bcdbd1db8d66277cb3c4f5f831160a7a6db596f306982f67`.
Matching-signer in-place installation succeeded; saved Go3/PitchAPI settings and
user data were retained. All 118 focused JVM tests pass. Both changed Sony lineup
render tests pass, including the two real-event formation patterns at three TV
logical widths. The earlier six catalogue/detail/reset/lineup fixture tests remain
recorded above; unchanged suites were not redundantly rerun.

The user physically confirmed live Fenerbahçe/Liverpool picture, sound and
Lithuanian commentary. R10 then opened the same live programme through normal
Watch and resolved real PitchAPI data with the stored local key. The TV displays
22 starting-player markers and both coaches; measured home column counts are
1/4/3/3 (goalkeeper plus 4-3-3) and away counts 1/3/2/4/1 (rotated 4-2-3-1).
The provider supplied eleven original bench players on each side. No scores,
events, ratings, substitution or participation data was exposed or logged.

Native player identity remained `4f41270`, source loads 1, DRM key loads 1 and
audio lt while Lineups loaded. Decoded frames advanced 5→806→1790→2741 and live
position 14437→14453→14479→14517 seconds. No protected screenshots were taken.
Generated unprotected render: `build/go3-review/sony-lineups-r10-fixture.png`.
Physical confirmation of corrected formation/readability and final bench focus/
scrolling checks are being completed on this live event only.


### Real-event acceptance and name/bench refinement (R11)

On the actual live Fenerbahçe/Liverpool match, D-pad traversal reached the last
original bench player while video continued. Center opened full-name player
details; Back closed that dialog first and restored the same bench focus. A
subsequent Back closed Lineups; reopening immediately reused the successful
session data and restored header focus. Player `4f41270`, source/key loads 1 and
Lithuanian audio stayed unchanged; decoded frames advanced to 8,690 at live
position 14,748 seconds. Both coaches and all 22 pitch markers remained available.

The user physically accepted the corrected formations and playback, requesting
two text refinements: short surnames such as Muñoz/Gómez must stay on one line,
and both original benches should have single-line, equal-height rows for comparison.
R11 reuses existing AutoResizeText for short pitch surnames and bench labels,
retains full-name accessibility/details, fixes bench rows at 44dp, reserves equal
coach-header height, and displays each original bench count. Longer pitch surnames
can still use two lines. No coordinates, original order or player data changed.
The new TV typesetter regression checks single-line/non-clipped Muñoz/Gómez,
single-line bench names and equal row heights. No protected video capture is used.


### Live/start-over choices and sports-only controls (R15)

The user requested Watch live versus Start from beginning, sports-only Lineups,
and removal of the visible Refresh action on events pages. Watch on a live Go3
programme now opens a remote-accessible choice, defaulting to Watch live.
Start from beginning requires an original programme start time and either literal
subscriber-context startoverAvailable=true or the exact live channel's literal
startOver=true, following the official live player's separate capability paths. The official web
frontend fetches both products/lives/programmes/{id} and
subscribers/products/lives/programmes/{id}; authentication on the public endpoint
alone still reports anonymous flags. Only allowlisted capabilities are retained; no account response payload enters
navigation, presentation or logs. The official continue/start-over choice also
checks the live channel's startOver capability. Fenerbahçe/Liverpool's channel
6886504 explicitly enables it even though its programme flag remains false;
requiring only the programme flag incorrectly disabled the official channel path.

Beginning uses the same entitled LIVE stream and normal Widevine session. Its
seek target is the programme start epoch minus Media3's manifest window start,
validated inside the seekable DVR window. It never substitutes another programme,
CATCHUP recording or the unrelated beginning of a channel buffer. If the exact
start is outside the window, playback stops with a neutral Watch live instruction.
Go live in the existing overlay returns to its default live position without
replacing the player/source/DRM session. Exact replay routing remains unchanged.

Lineups now requires explicit sport/sports content metadata. Movies, series and
unknown content have no Lineups button; a content change closes the panel and
invalidates pending work. Visible Refresh buttons were removed from both the
catalogue and programme details. Remote Options/Menu performs explicit refresh;
the two-minute cache, stale label and error Retry remain.

R13 passed 123 focused JVM tests and all ten isolated Sony UI tests. Tests exercise
the real Watch choice and selected navigation mode, unavailable start-over and
nested Back, movie/sport/series toggle changes, real catalogue D-pad/card/Menu
handlers, replay destinations, upcoming Watch, reset confirmation, 22-player
formation geometry, short single-line surnames, longer two-line names, equal
single-line original bench rows/counts and three TV logical widths. The generated
unprotected capture is build/go3-review/sony-lineups-r13-names.png. No licensed
video or key-entry captures were taken. R15 adds subscriber-context and the official channel start-over capability path,
and replaces pill clipping around pitch markers with compact rounded rectangles
so longer two-line names remain fully visible. Final checks follow below.


### Final R15 installed acceptance

Review APK: `build/go3-review/nuvio-go3-lineups-1073-r15-armeabi-v7a.apk`.
SHA-256: `39673a5cbfa067f80ecb65ecb98dc09f6875350c2c658b06810e172a1e721fb3`.
Matching-signer in-place installation preserved user data and local Go3/PitchAPI
settings. All 124 focused JVM tests and all ten isolated Sony fixture UI tests
pass. `git diff --check` passes. Final generated unprotected name/bench render:
`build/go3-review/sony-lineups-r15-names.png`; the rectangular marker clipping fix
was visually checked in addition to text-layout assertions.

Actual current Fenerbahçe/Liverpool (programme 12716199) was opened through the
normal UCL card → Watch → Start from beginning. Player `799079e`, source/key loads
1/1, audio lt decoded 46→746→1,946 frames at positions 10,406→10,420→10,444 seconds.
Go live sought to 14,464 seconds with the same player/source/keys/audio and
continued decoding (2,916 frames). Opening Lineups advanced to 4,302 frames and
14,492 seconds on the same player with 22 real confirmed pitch markers and both
coaches. The current provider response labelled both sides 4-2-3-1; original
sanitized formation coordinates were retained. Restore and Back returned to the
same exact programme detail.

A fresh Watch live choice started a normal licensed live session at 14,408 seconds
with decoded frames and lt audio. A subsequent Start from beginning started at
10,214 seconds (the sliding DVR window's relative position changes over time),
again with decoded frames and lt. The user physically confirmed that this shows
the beginning rather than live, with picture, sound and Lithuanian commentary:
“yes everything looks good”. The TV was left playing that requested current event
from the beginning. No other programme was played during these final checks.

To test: Discover (or Search → Discover) → Sports → Go3 Champions League → current
live event → Watch, then choose Watch live or Start from beginning. During a live
programme, the normal overlay offers Go live. Lineups appears only for sport/sports
content. Event pages have no visible Refresh button; Options/Menu refreshes.
Short surnames use one line; both original benches use aligned single-line rows
and original counts. All prior Lithuanian default, exact replay routing and
local-only account behavior remain. Source changes are uncommitted and nothing
was pushed. Account-token expiry and DRM-license expiry/renewal remain unverified;
this remains an experimental, unofficial Go3 integration.


### Diagnostic-free viewing update (R16)

At the user's request, removed the temporary Go3 proof-text overlay and its
recurring two-second UI update from PlayerScreen. Preserve Widevine capture
protection and all player/session behavior. This is a presentation-only change;
the R15 feature tests remain the last full suite, and R16 requires APK assembly,
absence of the diagnostic text in production DEX, signer verification and
in-place installation. Do not add permanent on-screen diagnostics for routine
quality investigation.

The user reports quality drops during normal full-screen playback. Read-only,
allowlisted active-player logs showed a selected supported 1920x1080 AVC track.
Code review found automatic Media3 adaptive selection for Go3 and no explicit
Go3 video-size or bitrate cap. This is not a time series or proof of sustained
resolution; the cause of the reported drops is unconfirmed. A speed test's
throughput does not establish sustained Go3 CDN throughput. Prioritize the
requested overlay removal and conserve the user's remaining usage budget;
do not force the highest representation without measuring CDN delivery, buffer
health and actual decoder input-format changes.

R16 APK: build/go3-review/nuvio-go3-lineups-1073-r16-armeabi-v7a.apk.
SHA-256: 3be7233347aa1b4ed055f0a492ef7bbc44f7e77183cc3cfab0f8e93ff4eb3d67.
Assembly and diff checks passed; production DEX contains no Go3 diagnostic overlay
text or fake authentication test strings. Matching signer verified. No quality
selection behavior was changed in this urgent presentation-only update.


### Europa League and Conference League (R17)

Both competitions are permanent built-in Sports sources. Go3's public programme
details identify Europa League as `uefaeuropaleague` (6886318) and Conference League
as `uefaeuropaconferenceleague` (6886320). They use the shared catalogue, cache,
availability, and exact-programme routing. Cards strip the competition prefix
while details retain the full programme title. No playback tuning changed.

The matching-signer R17 update installed in place on the Sony. All 41 focused Go3
JVM checks passed, including both Sports selector routes. The real selector
showed both catalogues. Europa League showed Sturm Graz–Celje as Upcoming;
Conference League showed Thun–Hearts Live and two upcoming programmes.
Thun–Hearts (12716264) opened its exact detail and both live playback choices.
Watch live opened the player, which reported supported 1920×1080 AVC track
metadata. Lineups opened and showed its neutral unavailable/Retry state; no
confirmed data was displayed for this match. Full-screen playback was restored.
Quality drops remain unverified; track metadata is not a decoder time series.
The user physically confirmed picture, sound and Lithuanian commentary.

### Lineup matching and catalogue polish (R18)

Go3's Thun–Hearts title now matches FC Thun and Heart of Midlothian by
exact club aliases on either side of the fixture separator. The matcher still
requires a unique fixture and rejects similar club names. Verified on the Sony:
both confirmed formations and all 22 starters were shown for live Thun–Hearts.
Search's embedded Discover screen refreshes the Go3 catalogue while visible.
A catch-up window without a recording ID no longer offers replay.

All 128 focused JVM checks passed. The Sony received 1080p at 8 Mbps, then
576p at 1 Mbps, 720p at 2 Mbps, and returned to 1080p in the same playback session.
These are actual decoder input formats. The bandwidth at decode time can differ
from the bandwidth at segment selection time, so these measurements alone do
not identify the cause of the downshift.

### Lineups layout and live video fallback (R19–R20)

Keep both bench columns in place when only one side has a confirmed lineup.
Offer Retry on an incomplete or unavailable lineup. The ten isolated Sony UI
fixtures passed on R19.

In a full-screen Thun–Hearts session on R19, the 1080p/8 Mbps video segment
returned HTTP 404 and was canceled. Media3 immediately selected 720p/2 Mbps
with about 30 seconds buffered and a 256 Mbps estimate, then requested 576p.
The decoder displayed those segments after consuming the older buffer.
This measured drop followed a segment failure.

R20 gives the first HTTP 404 for a Go3 live video media segment one delayed
retry of one second before selecting another representation. Existing CDN
location fallback still applies. Replay, audio, manifest, entitlement and
Widevine handling use their existing policies. The debug build keeps bounded
numeric format and failure tracing in internal logs, separate from presentation.

On R20, the same 1080p segment returned 404 again one second after the initial
404. The player fell back to 720p and then 576p. R21 tests a bounded five-second
retry window with delays of one, two and two seconds. After that window,
Media3 can exclude the failing track as usual.

R21 installed in place with the matching signer. All 130 focused JVM tests
passed, including the retry window, persistent-failure fallback and alternate
CDN behavior. On the same live Thun–Hearts programme, the trace captured four
1080p segment 404s with retry enabled. It recorded no lower video representation
during approximately 115 seconds of observation. The Sony showed both confirmed
lineups with 22 starters, and Restore video returned to full screen. Sustained
provider failures can still trigger Media3's ordinary fallback.

Review APK: build/go3-review/nuvio-go3-lineups-1073-r21-armeabi-v7a.apk.
SHA-256: 0aa0540291b61b307ced3f22a6c5ac0cff82664ff155190108a88bf65704c357.

### Go3 live DVR and replay QA (R22–R23)

The player exposes a Go3 DVR range only when Media3 reports a seekable window.
Its lower bound is the retained programme start and its upper bound is the
manifest's default live position. The remote media keys and the focused seek
bar use that range. The live status has a red dot to the right of Live within
six seconds of the default live position. Behind that range, controls show the
delay and offer Go live, which returns focus to the Play/Pause control.

On the Sony, Thun–Hearts verified both remote media seek directions, D-pad seek
bar focus and scrubbing, the Live/Go live transition, and Start from beginning.
The Slavia Praha–Arsenal replay opened its exact recording and showed 22
confirmed starters. PitchAPI supplied the home club as Slavia Prague. The alias
checks continue to require both teams and a unique fixture. Back closed Lineups
and restored focus to the exact replay card after leaving playback.

R23 also preserves the first seek press when the Go3 pause screen is visible,
removes the add-on Sources shortcut from Go3, and keeps Go3 controls visible for
five seconds during playback.

R23 installed in place with the matching signer. All 132 focused JVM tests and
ten isolated Sony UI tests passed. After the pause cover appeared, the first
rewind press moved behind live while keeping playback paused. Go live resumed
the event and moved focus back to Pause. The Live label and DVR seek bar were
present on the playing controls, and the add-on Sources shortcut was absent.
The Sony was left playing Thun–Hearts at the live edge.

Review APK: build/go3-review/nuvio-go3-lineups-1073-r23-armeabi-v7a.apk.
SHA-256: 88a23ac541ff8238c2ce2dca2fda5d4db692259f6fc4a3f8d89964fb7c426c0d.

### Detail screen and lineup focus (R24)

The programme detail shows Watch and conditional Retry. Remote Back remains
available, and the exact programme locator stays in the playback route.
Pitch surnames try the largest readable single-line size before wrapping.
When the player controls close over Lineups, focus returns to Player controls.

All ten Sony UI tests passed on R24. On live Thun–Hearts, the controls timed
out and returned focus to Player controls. The first Right press moved to
Restore video. The detail screen showed Watch without the URL or Back button.

Review APK: build/go3-review/nuvio-go3-lineups-1073-r24-armeabi-v7a.apk.
SHA-256: 45e0e53d45c13131be08cd1c908990b6b3b4dde53eb26bf40c2e3eddba0cbab7.

## R31 catalogue and lineup presentation

Go3 public programme metadata confirmed category IDs and returned programme
rows for La Liga (34), Bundesliga (19), Ligue 1 (3), Serie A (42), and FA Cup
(12) at verification time. Counts are listings and may include upcoming or
expired programmes. Entitlement remains a playback check.

The player control uses the supplied Formation symbol with existing player focus
and an accessible Lineups label. The panel title is Lineups and omits the
redundant fixture title. The existing pitch geometry uses a greener fill, pitch
markers use surnames with compound particles, and bench rows use initials.
Automatic fixture matching compares both parsed team labels, tolerates ordinary
club affixes and acronym forms, and still requires a single fixture and explicit
provider confirmation.

R31 passed 62 focused JVM tests and 10 isolated Sony UI tests. The normal
Go3 catalogue → Watch → Formation control route displayed both confirmed
PitchAPI lineups for Viking–Sabah, PSV–Club Brugge, Porto–Napoli and
Villarreal–PSG on the Sony. The observed provider labels included Sabah FK,
PSV Eindhoven, FC Porto, and Paris Saint-Germain. This verified the lineup
lookup and TV presentation. The five new catalogues were verified through
public metadata and isolated selection tests.

## R32 domestic replay fixture matching

Go3's domestic replay titles can abbreviate teams to labels such as “Athletic”
or “Borussia”. Public programme details can supply independent COMPETITOR
category slugs even when the cards omit them. The player lookup reads the
already-loaded exact programme by ID, compares both club identities in the
same league, and preserves the unique-fixture and confirmed-lineup requirements.
Untagged domestic programmes can use distinctive short club labels and standard
club affixes. Generic labels such as “Borussia” still need structured identity.
Rebroadcast timestamps do not replace an original fixture date.

R32 showed both confirmed sides on the Sony for Athletic–Real Sociedad and
Werder–Hoffenheim. The tested Bayern–Borussia rebroadcast remained unavailable
because its Go3 identity used Bayern Munich and the provider used Bayern
München. R33 normalizes city spellings and recognizes distinctive shortened
provider labels against tagged club identities. The same Bayern–Borussia
rebroadcast then showed confirmed Bayern München and Borussia Dortmund lineups
on the Sony. R33 passed 64 focused JVM tests. Ligue 1 playback and lineup
coverage were not exercised on the device.

## Regular app installation

The regular Sony app uses package `com.nuvio.tv`. Android requires the installed
release signing identity for an in-place upgrade. Verify the candidate APK's
certificate against the installed APK before using `adb install -r`. The
`com.nuviodebug.com` review build has a separate certificate and app data.
After a signed regular update, configure Go3 and PitchAPI privately in the
regular app. Their local sessions and key are scoped to each package.

The temporary debug feasibility screen and its activity were removed after the
normal catalogue and player paths passed acceptance. The isolated fixture
runner remains available for tests.
