#!/bin/bash
# Reads the pushed tag (e.g. "v1.2.3"), patches app/build.gradle.kts's versionCode/versionName
# to match, and prepares VTag/VName/ApkName env vars for the rest of release.yml.
# Release/Telegram message body (Body/TMessage) is built separately by extractChanges.sh
# from changelog.txt, which runs right after this step.

NEWVERNAME=${GITHUB_REF_NAME/v/}
NEWVERCODE=${NEWVERNAME//.}

echo 'VTag<<EOF' >> $GITHUB_ENV
echo ${GITHUB_REF_NAME} >> $GITHUB_ENV
echo 'EOF' >> $GITHUB_ENV

echo 'VName<<EOF' >> $GITHUB_ENV
echo 'Obsidian v'$NEWVERNAME >> $GITHUB_ENV
echo 'EOF' >> $GITHUB_ENV

echo "ApkName=Oxydian-release-$NEWVERNAME.apk" >> $GITHUB_ENV

sed -i 's/versionCode.*/versionCode    = '$NEWVERCODE'/' app/build.gradle.kts
sed -i 's/versionName    =.*/versionName    = "'$NEWVERNAME'"/' app/build.gradle.kts
