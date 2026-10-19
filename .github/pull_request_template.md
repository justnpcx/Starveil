## Summary

<!-- One or two sentences. What changes, and for whom? -->

## Why

<!-- The reason, not the diff. What was wrong, or what is now possible? -->

## Type

- [ ] fix
- [ ] feat
- [ ] refactor (behaviour unchanged — say which tests prove it)
- [ ] docs
- [ ] build / CI

## Verification

<!--
What you actually ran, and what you saw. "Verified on the V2520A" is not
verification — give the sequence, the log line, or the number.
-->

Commands:

```
./gradlew checkTranslations
./scripts/leak-scan.sh
./gradlew :app:assembleDebug testDebugUnitTest lintDebug
```

Device (if applicable):

<!-- version, Android, steps, expected vs observed -->

## Checklist

- [ ] The change has a reason in this description, not only in the diff
- [ ] `checkTranslations` passes (new strings exist in every locale)
- [ ] `leak-scan.sh` is clean (no real hostnames, tokens, or local paths)
- [ ] Unit tests cover the behaviour, or the PR says why they cannot
- [ ] Lint is clean
- [ ] No node links, UUIDs, passwords, subscription URLs or keys in the diff,
      screenshots, or logs
- [ ] UI changes include a screenshot or recording
- [ ] Documentation updated when behaviour or configuration changed

<!-- A PR whose CI is red is not reviewed. See CONTRIBUTING.md. -->
