# R8 / ProGuard rules for the Triplo release build.
#
# KorGE and the Google Mobile Ads SDK ship their own *consumer* rules (bundled inside their
# artifacts), and R8 applies those automatically — so this file is intentionally empty.
#
# Add app-specific -keep rules here only if a release build is found to strip something the
# game needs at runtime (symptoms: works in debug, crashes/misbehaves in the release build).
