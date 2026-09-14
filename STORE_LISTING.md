# Store listing & submission guide

Practical guidance for submitting the built app to Google Play, Amazon Appstore, Samsung Galaxy Store, and the Xiaomi/Vivo/Oppo family — written to avoid the most common review rejections for a utility app with ads. None of this is legal advice; it's a checklist, not a guarantee any store approves any specific submission.

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
> • Works with no internet connection
> • Free, supported by a small bottom banner ad and an occasional closeable ad on launch — no interstitial, rewarded, or video ads, ever
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
| Does your app collect or share user data? | **Yes** (only via the AdMob/UMP SDKs — the app's own code collects nothing) |
| Data types collected | **Device or other identifiers** (advertising ID) |
| Is data encrypted in transit? | Yes (handled by the AdMob SDK itself) |
| Can users request data deletion? | Not applicable — nothing is stored server-side by this app; advertising ID behavior follows the user's own device-level ad settings |
| Purpose | **Advertising or marketing** |
| Is data collection required or optional? | Optional in effect — ads (and so this data flow) simply don't request without consent where consent is required, and a user can decline ad personalization via Menu → Ad privacy choices |

Do **not** declare file contents, filenames, or any file metadata as collected — none of it is; the compression engine has no network access at all (see `PRIVACY.md`).

**Ads declaration:** mark "Yes, my app contains ads." **Target audience / content rating:** answer as general audience, not primarily directed at children — the ad configuration in this codebase already reflects that (see `AndroidManifest.xml`'s `DELAY_APP_MEASUREMENT_INIT` note and `PRIVACY.md`'s "Children's privacy").

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
- `compileSdk`/`targetSdk` are set to 36 (Android 16) in this build, matching Play's current requirement (in effect since August 31, 2026) — see `README.md` "If the Android build fails" if a future Play policy bumps this further.
- New developer accounts require a short closed-testing period with a minimum tester count before Play allows a production release — factor this into your timeline.

### Amazon Appstore
- Accepts a signed APK directly (no AAB requirement) — use the release `.apk` artifact.
- Runs its own app scan partly independent of Google Play services availability — since this app's ads depend on Google Play services being present, note in your submission that ads gracefully do not appear on devices without Play services (the banner/App Open ad simply never loads, per the "never a placeholder" behavior in `README.md` "Ad strategy") rather than crashing.

### Samsung Galaxy Store
- Also accepts a signed APK. Samsung's review specifically checks that the app functions correctly on Samsung's own device/One UI skin — since this app is a standard WebView-based Tauri app with no Samsung-specific APIs involved, no special handling should be needed, but test on a real Samsung device (or Samsung's remote test lab) before submitting if possible.
- Samsung independently reviews ad placement for intrusiveness — the "no interstitial/rewarded/video, banner + one closeable app-open ad" policy in this codebase (see `README.md` "Ad strategy") is deliberately on the conservative end specifically to clear this kind of review comfortably.

### Xiaomi (GetApps) / Vivo App Store / Oppo App Market
- All three accept signed APKs and run comparatively strict manual review, especially around permissions and ad behavior — the minimal permission set and no-interstitial ad policy in this codebase were chosen with exactly this in mind.
- These stores often require a **local contact/registration** (varies by store and your region) separate from the app submission itself — check each store's current developer registration requirements before submitting, as these change independently of anything in this codebase.
- Expect a longer manual review window than Play/Amazon for these three.

---

## Common rejection reasons this build already accounts for

- **"Privacy policy doesn't match actual data collection"** — `PRIVACY.md` was rewritten specifically to accurately describe the AdMob data flow rather than leave a stale "we collect nothing" claim once ads were added (see `PRIVACY.md`'s "The Android app is free and shows ads" section).
- **"Ad implementation is disruptive"** — no interstitial/rewarded/video anywhere; see "Ad strategy" in `README.md`.
- **"Missing ad consent mechanism"** — UMP consent flow + a persistent "Ad privacy choices" menu entry are both implemented, not just the first-launch prompt.
- **"Unjustified permissions"** — only two permissions, both directly tied to a visible feature (ads), no storage permission needed at all.
- **"App icon contains illegible text/clutter"** — replaced; see `README.md` "App icon".
- **"Broken/placeholder ad space"** — structurally impossible in this build; the banner container has zero height until a real ad loads (see "Ad strategy").

None of this guarantees approval — store policies and reviewers vary — but each addresses a specific, common, named rejection reason rather than being a generic best-effort claim.
