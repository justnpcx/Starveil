package com.justnpcx.starveil.benchmark

/**
 * The app under test.
 *
 * Every benchmark and the profile generator point at the `benchmark` variant of
 * :app — release settings, debug-signed, non-debuggable — so the numbers
 * describe something close to what ships rather than a debug build that has
 * been flattered by assertions being off and AOT being different.
 */
const val PACKAGE = "com.justnpcx.starveil"
