# Store listing & submission guide

Practical guidance for submitting the built app to Google Play, Amazon Appstore, Samsung Galaxy Store, and the Xiaomi/Vivo/Oppo family — written for the first ad-free store release and the later optional monetized release. None of this is legal advice; it's a checklist, not a guarantee any store approves any specific submission.

---

## Suggested listing copy

**Short description (≤80 chars):**
> Compress photos, videos, audio & PDFs — 100% on your device. No uploads.

**Full description starting point:**
> DD Compressor shrinks photos, videos, audio, and PDF files without ever sending them anywhere. Everything happens on your own device — no upload, no account, no server. Works fully offline once installed.
>
> • Compress images, video, audio, and PDFs
> • Pick a target size or quality — see the result before you save
> • Nothing you compress ever leaves your device
> • Compression works offline
> • First store release: free and ad-free
> • Later monetized release: AdMob banner + occasional interstitial after 20 completed compression jobs
>
> Your files are yours. This app doesn't have a server to send them to even if it wanted to.

Feel free to rewrite freely — this is a starting point that happens to also pre-empt the two questions reviewers most often have about a "privacy" claim (does it actually not upload anything, and does the ads claim match reality).

**Suggested category:** Tools / Productivity (Play Store: "Tools"; Amazon: "Utilities"; Samsung/Xiaomi/Vivo/Oppo have similar Tools/Utilities categories).

---

## Privacy policy URL

Every store in this list requires a **public, hosted URL** for the privacy policy — a file in this repo isn't enough on its own. `web/PRIVACY.md` is written and ready; host it somewhere with a stable URL (GitHub Pages serving this repo already gives you one for free, e.g. `https://<username>.github.io/<repo>/PRIVACY.md`, or convert it to an HTML page and link that instead — either satisfies every store's requirement for a policy URL, not a login-gated or PDF-only one).

---

## Google Play — Data safety section

The Play Console's Data safety form asks what data the app collects/shares. Based on exactly what's in this codebase:

| Question | Answer for this app |
|---|---|
| Does your app collect or share user data? | **No in the first ad-free build.** A later monetized build will use the AdMob/UMP SDKs. |
| Data types collected | **None in the first ad-free build.** Later monetized builds may process advertising/device identifiers through AdMob. |
| Is data encrypted in transit? | Not applicable to ads in the first ad-free build. |
| Can users request data deletion? | Not applicable — nothing is stored server-side by this app; advertising ID behavior follows the user's own device-level ad settings |
| Purpose | None in the first ad-free build; advertising/marketing in later monetized builds. |
| Is data collection required or optional? | No advertising data flow in the first ad-free build. Later monetized builds request ads only after the applicable consent flow. |

Do **not** declare file contents, filenames, or any file metadata as collected — none of it is; the compression engine has no network access at all (see `PRIVACY.md`).

**First ad-free release:** mark "No" for contains ads. **Later monetized release:** update the store declaration to "Yes" and complete the applicable AdMob/privacy disclosures. **Target audience / content rating:** answer based on the actual release being submitted.

**Content rating questionnaire:** this is a utility app with no user-generated content, no violence, no user communication features — should land in the lowest rating tier (e.g. "Everyone" / PEGI 3) on a standard IARC questionnaire.

**Permissions:** only `INTERNET` and `ACCESS_NETWORK_STATE` (see below) — Play Console won't ask for a Permissions Declaration Form beyond what's auto-flagged for these, since neither is a "dangerous" permission requiring special justification.

---

## Permissions — the actual list, and why each one exists

Every store in this list scrutinizes permissions against what the app actually does; overly-broad or unjustified permissions are one of the single most common rejection reasons on the stricter OEM stores (Xiaomi/Vivo/Oppo in particular). This app requests exactly two:

| Permission | Why | Notes for reviewers |
|---|---|---|
| `INTERNET` | Loading ads, checking connectivity | Compression itself works with this permission denied entirely — it's genuinely only for ads |
| `ACCESS_NETWORK_STATE` | Checking whether the device is online before attempting to load an ad | Prevents a pointless network attempt when offline; no data is read from this beyond "connected: yes/no" |

**No storage/media permission is requested at all.** Saving a compressed file uses Android's scoped, app-private external storage (`getExternalFilesDir()`), which needs no runtime permission on any currently-supported Android version — this is worth stating explicitly in review notes on stores that ask, since "why does a file tool need storage access" is a very common reviewer question, and the honest answer here is that it doesn't need broad storage access at all.

---

## Store-specific notes

### Google Play
- Requires an Android App Bundle (`.aab`) as of current policy — use the signed AAB from the release build, not the APK, for the Play Console upload.
- `compileSdk`/`targetSdk` are set to 36 (Android 16) in this build, matching Play's current requirement (in effect since August 31, 2026) — see `Blueprint.md` "If the Android build fails" if a future Play policy bumps this further.
- New developer accounts require a short closed-testing period with a minimum tester count before Play allows a production release — factor this into your timeline.

### Amazon Appstore
- Accepts a signed APK directly (no AAB requirement) — use the release `.apk` artifact.
- Runs its own app scan partly independent of Google Play services availability — the first release has no ad SDK. For a later monetized build, verify the store's current ad/Google Play services requirements before submission.

### Samsung Galaxy Store
- Also accepts a signed APK. Samsung's review specifically checks that the app functions correctly on Samsung's own device/One UI skin — since this app is a standard Capacitor-based WebView app with no Samsung-specific APIs involved, no special handling should be needed, but test on a real Samsung device (or Samsung's remote test lab) before submitting if possible.
- Samsung independently reviews ad placement for intrusiveness — the later monetized build should be reviewed against the store's current ad-placement policy before submission.

### Xiaomi (GetApps) / Vivo App Store / Oppo App Market
- All three accept signed APKs and run comparatively strict manual review, especially around permissions and ad behavior — the first ad-free build has no advertising SDK; review the later monetized build separately for the store's current ad and permission requirements.
- These stores often require a **local contact/registration** (varies by store and your region) separate from the app submission itself — check each store's current developer registration requirements before submitting, as these change independently of anything in this codebase.
- Expect a longer manual review window than Play/Amazon for these three.

---

## Common rejection reasons this build already accounts for

- **"Privacy policy doesn't match actual data collection"** — keep `PRIVACY.md` aligned with the exact build being submitted. The first release is ad-free; the later monetized release must disclose AdMob/UMP data processing.
- **"Ad implementation is disruptive"** — the banner is kept separate from interactive controls, and the interstitial is only attempted after 20 completed compression jobs.
- **"Missing ad consent mechanism"** — UMP consent flow + a persistent "Ad privacy choices" menu entry are both implemented, not just the first-launch prompt.
- **"Unjustified permissions"** — the first build should request only permissions actually required by its enabled features; do not add an ad-related permission/SDK until the monetized build is enabled.
- **"App icon contains illegible text/clutter"** — replaced; see `Blueprint.md` "App icon".
- **"Broken/placeholder ad space"** — the first ad-free build has no ad space. The later monetized build should reserve space only when a real native banner is loaded.

None of this guarantees approval — store policies and reviewers vary — but each addresses a specific, common, named rejection reason rather than being a generic best-effort claim.
