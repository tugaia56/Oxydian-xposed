#!/bin/bash
# Reads the pushed tag (e.g. "v1.2.3") for the display versionName, patches
# app/build.gradle.kts's versionCode/versionName, and prepares VTag/VName/ApkName env vars
# for the rest of release.yml. Release/Telegram message body (Body/TMessage) is built
# separately by extractChanges.sh from changelog.txt, which runs right after this step.
#
# versionCode is deliberately NOT derived from the tag's own digits (e.g. "v1.2.3" -> 123)
# — Android refuses to install an APK whose versionCode is lower than what's already
# installed, so tying it to the display version risks a real downgrade-block the day the
# display version is ever reset/renumbered downward (happened 2026-09-30: v1.0.3 -> v1.0.0).
# Instead it's just incremented by 1 from whatever is already committed in
# build.gradle.kts (this script runs on a fresh clone that already has the previous
# release's committed value — see "Version Bump Commit" below), so it always goes up no
# matter what the tag says.

NEWVERNAME=${GITHUB_REF_NAME/v/}
CURRENT_VERCODE=$(grep -oP 'versionCode\s*=\s*\K[0-9]+' app/build.gradle.kts)
NEWVERCODE=$((CURRENT_VERCODE + 1))

echo 'VTag<<EOF' >> $GITHUB_ENV
echo ${GITHUB_REF_NAME} >> $GITHUB_ENV
echo 'EOF' >> $GITHUB_ENV

echo 'VName<<EOF' >> $GITHUB_ENV
echo 'Oxydian v'$NEWVERNAME >> $GITHUB_ENV
echo 'EOF' >> $GITHUB_ENV

echo "ApkName=Oxydian-release-$NEWVERNAME.apk" >> $GITHUB_ENV

sed -i 's/versionCode.*/versionCode    = '$NEWVERCODE'/' app/build.gradle.kts
sed -i 's/versionName    =.*/versionName    = "'$NEWVERNAME'"/' app/build.gradle.kts
