# Releasing Sky's Realistic Weather

The build, artifact audit, CodeQL and wrapper checks run on pull requests and
version branches. Gameplay, multiplayer and long climate checks run locally in
disposable profiles. Their results are kept separately from hosted CI.

Publication uses the same approach as Sky's Building Pieces and Sky's Terrain
Smoother. The MMD workflow builds one bundle from the checked commit, verifies
its contents and checksums, then publishes those files to Maven, CurseForge
and GitHub in that order. A tag by itself does not publish anything.

The bundle contains the main mod, compile-only API, sources and Javadoc jars.
Maven and GitHub receive all four. CurseForge receives the main mod with its
sources and Javadocs attached. Only the main mod belongs in a playable profile.

Run **Release Sky's Realistic Weather** from the MMD repository's Actions tab.
Enter the four-component version, choose the CurseForge release level and
confirm publication. The version suffix selects the Minecraft branch. Keep
each version's optional relationships in `publishing/curseforge-relations.json`;
the uploader reads this file rather than relying on `mcmod.info`.

Use `validate_access_only` to check the shared credentials without building,
tagging or publishing. The workflow needs `CURSEFORGE_TOKEN`,
`MAVEN_UPLOAD_URL`, `MAVEN_UPLOAD_USERNAME` and `MAVEN_UPLOAD_PASSWORD`.
Credentials must be available to this repository before publication starts.
Fork workflows never publish.

Local release checks:

```text
gradlew check build verifyReleaseArtifacts writeReleaseChecksums -PweatherProductionOnly=true
```

The production-only option skips development compatibility profiles and probe
jars. It keeps the normal runtime, unit tests and artifact checks. Ordinary
Eclipse builds still prepare their separate compatibility launch files.

The release audit checks Java compatibility, version metadata, translations,
licences and unwanted archive entries. A bundle test also checks that a changed
jar is rejected. Maven publication accepts only the prepared, verified bundle.

After publication, check every destination and compare the public downloads
with the bundle's checksums. An accepted CurseForge upload can take time to
appear publicly. Keep its file ID and check its status before retrying; a
timeout does not prove that the upload failed. Rerun only the failed stage
when earlier stages have succeeded.
