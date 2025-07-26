#!/bin/bash
set -e

CURRENT_TAG="ver-1.21.8"
RELEASE_NOTES="RELEASE.md"

# Branch name
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
echo "✨Current branch: $CURRENT_BRANCH"

# Latest tag name
LATEST_TAG=$(git describe --tags --abbrev=0)
if [ -z "$LATEST_TAG" ]; then
  LATEST_TAG=$(git rev-list --max-parents=0 HEAD)
  echo "⚠️No previous release found. Using initial commit."
else
  echo "✨Latest tag: $LATEST_TAG"
fi

# Commit of the latest tag
LAST_RELEASE_COMMIT=$(git rev-list -n 1 "$LATEST_TAG")
echo "✨Last release commit: $LAST_RELEASE_COMMIT"

# Commits log
COMMIT_LOG=$(git log "$LAST_RELEASE_COMMIT"..HEAD --pretty=format:"- [\`%h\`](https://github.com/${GITHUB_REPO}/commit/%H) %s (%an)")
if [ -z "$COMMIT_LOG" ]; then
  COMMIT_LOG="⚠️No new commits since $LATEST_TAG."
else
  echo "✅Commits log generated"
fi

# Release notes header
echo "" >> $RELEASE_NOTES

# Commits log
{
  echo "### 📜 Commits:"
  echo ""
  echo "$COMMIT_LOG"
  echo ""
  echo "### 🔒 Checksums"
} >> $RELEASE_NOTES

# Get checksums
file="./fish-server/build/libs/fish-paperclip-1.21.8-R0.1-SNAPSHOT-mojmap.jar"
if [ -f $file ]; then
  SHA256=$(sha256sum $file | awk '{ print $1 }')
  SHA512=$(sha512sum $file | awk '{ print $1 }')
  FILENAME=$(basename $file)

  {
    echo "|           | $FILENAME |"
    echo "| --------- | --------- |"
    echo "| SHA256    | $SHA256   |"
    echo "| SHA512    | $SHA512   |"
  } >> $RELEASE_NOTES

  echo "🔒Checksums calculated:"
  echo "   SHA256: $SHA256"
  echo "   SHA512: $SHA512"
else
  echo "⚠️No artifacts found." >> $RELEASE_NOTES
fi

# Delete current release tag
if git show-ref --tags $CURRENT_TAG --quiet; then
  {
    gh release delete $CURRENT_TAG --cleanup-tag -y -R "${GITHUB_REPO}"
  }
fi
echo "🚀Ready for release"
