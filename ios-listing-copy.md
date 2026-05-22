# Triplo — iOS App Store listing copy (draft)

Drafted to fit App Store Connect character limits so it can be pasted in directly once the
App Store record exists. Tweak to match the Android Play-listing voice before submitting.

## Names & short copy

| Field | Limit | Draft |
|---|---|---|
| App name | 30 chars | `Triplo` |
| Subtitle | 30 chars | `Powers-of-three merge puzzle` |
| Promotional text | 170 chars | `Tap-merge powers of three on a 7×7 grid. Drop bombs and rockets when the board jams. Calm visuals, one hand, no accounts, no in-app purchases.` |

## Keywords (single field, comma-separated, 100 chars max)

```
merge,threes,blocks,brain,number,math,puzzle,casual,strategy,relax,offline,one-hand
```

83 chars — leaves room to swap in stronger keywords later. Apple stems automatically, so
no plurals are needed and the app name does not have to be repeated.

## Description (4000 chars max)

```
Triplo is a calm, fast-paced merge puzzle built around the powers of three.

Tap any group of three matching tiles to merge them into the next power: three 1s become a 3, three 3s become a 9, three 9s become a 27, and so on — all the way up to 19,683 and 59,049 if you can keep the chains going.

When the board jams up, your bombs blast a small area clear and your rockets sweep a full row away. Use them sparingly — power-ups are limited, and saving them for the right moment is where the strategy lives.

FEATURES
• A 7×7 grid built for one-handed play
• Powers of three: 1, 3, 9, 27, 81, 243, 729, 2,187, 6,561, 19,683, 59,049
• Bombs and rockets for when you get stuck
• Undo up to two moves each round
• Best-score tracking
• Share your final board as emoji to your friends
• A short interactive tutorial on first launch
• Calm vector art with a color-shifting background tuned to your highest tier
• Optional sound effects and haptic feedback
• Works fully offline — no account, no sign-in, no in-app purchases

Triplo is published by AllMeat Games.
```

~1,150 chars — room to expand if the Android description is longer.

## URLs to enter alongside

- **Privacy policy URL:** `https://triplo.club/privacy`
- **Marketing URL (Support URL / Marketing URL):** `https://triplo.club`
  - This is also the URL AdMob crawls for `app-ads.txt`, so it must match what we set in the
    Play Store listing's developer-website field. Keeping both stores on `triplo.club` lets a
    single `app-ads.txt` cover both apps.

## Categorization

- **Primary category:** Games → Puzzle
- **Secondary category:** Games → Casual
- **Age rating:** 4+ (no objectionable content; ads enabled)

## App Privacy labels (for the App Privacy section)

The AdMob SDK is the only data collector in the app. Declare:

| Data type | Linked to user? | Used for tracking? | Purpose |
|---|---|---|---|
| Device ID (IDFA) | Yes (when ATT granted) | Yes | Third-Party Advertising |
| Coarse Location (from IP) | No | No | Analytics, Advertising |
| Product Interaction (ad views/clicks) | No | Yes | Third-Party Advertising |

Nothing else — no account, no contacts, no health data, no purchases.
