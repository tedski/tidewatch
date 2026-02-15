# TideWatch CI/CD Setup Guide

This guide explains the CI/CD infrastructure for automated testing and deployment to Google Play.

## Overview

TideWatch uses GitHub Actions to automate:
- **Testing & Quality**: Unit tests, lint checks on every PR and push
- **Deployment**: Build signed APKs and upload to Google Play Internal Testing track
- **Version Management**: Extract versions from git tags (e.g., `v1.2.3`)
- **Secure Signing**: Credentials stored as GitHub secrets, never in code

## Workflows

### CI Workflow (`.github/workflows/ci.yml`)

Runs on:
- Every push to `main` branch
- Every pull request targeting `main`

Steps:
1. Checkout code
2. Set up Java 17
3. Run lint checks
4. Run unit tests
5. Build debug APK
6. Upload test/lint results as artifacts

**Result**: Fast feedback (5-10 minutes) on code quality and buildability

### Release Workflow (`.github/workflows/release.yml`)

Runs on:
- Git tags matching `v*.*.*` (e.g., `v1.0.0`, `v2.1.3`)
- Manual trigger via `workflow_dispatch`

Steps:
1. Checkout code
2. Set up Java 17
3. Decode keystore from GitHub secrets
4. Run tests and lint (blocks deployment if they fail)
5. Build signed release APK
6. Upload to Google Play Internal Testing track
7. Create GitHub Release with APK attached
8. Clean up keystore file

**Result**: Production-ready APK uploaded to Google Play (30-45 minutes)

## Prerequisites

### 1. Google Play Console Setup

#### Create Service Account
1. Go to [Google Play Console](https://play.google.com/console) → Settings → API access
2. Create new service account or link existing one
3. Grant permissions:
   - **Create releases** (required)
   - **View app information** (optional)
   - **Manage testing tracks** (required for Internal Testing)
4. Download JSON key file
5. Store contents in GitHub secret `PLAY_CONSOLE_SERVICE_ACCOUNT_JSON`

#### Enable App Signing
1. Go to your app → Release → Setup → App Integrity
2. Follow "App Signing" setup wizard
3. Upload your upload certificate (see Keystore Setup below)
4. Complete setup (Google Play will manage the release key)

### 2. Generate Upload Keystore

Generate the keystore locally (only once):

```bash
keytool -genkeypair -v \
  -keystore tidewatch-upload.keystore \
  -alias tidewatch \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass <STRONG_PASSWORD> \
  -keypass <STRONG_PASSWORD> \
  -dname "CN=TideWatch, OU=Development, O=TideWatch, L=San Francisco, ST=CA, C=US"
```

**Important**: Save the passwords securely!

#### Extract and Upload Certificate to Google Play

```bash
keytool -export -rfc \
  -keystore tidewatch-upload.keystore \
  -alias tidewatch \
  -file upload_certificate.pem
```

Upload `upload_certificate.pem` to Google Play Console (in App Signing setup).

#### Encode for GitHub Secrets

```bash
base64 -i tidewatch-upload.keystore | pbcopy
```

### 3. Configure GitHub Secrets

Repository → Settings → Secrets and variables → Actions

Add these secrets:
- `KEYSTORE_BASE64`: Base64-encoded keystore file (from `pbcopy` above)
- `KEYSTORE_PASSWORD`: Keystore store password
- `KEY_ALIAS`: Key alias (typically `tidewatch`)
- `KEY_PASSWORD`: Key password (same as store password)
- `PLAY_CONSOLE_SERVICE_ACCOUNT_JSON`: Full JSON contents of service account key

**Security**: These are encrypted and only exposed to workflows. Never commit them to git.

## Version Management

### Semantic Versioning

Format: `v<major>.<minor>.<patch>`

Examples:
- `v1.0.0` - Initial release
- `v1.1.0` - New features, backward compatible
- `v1.0.1` - Bug fix
- `v2.0.0` - Breaking changes

### Version Code Calculation

`versionCode` is automatically calculated from the tag:

- `v1.2.3` → `versionCode = 10203` (1×10000 + 2×100 + 3)
- `v2.0.0` → `versionCode = 20000`
- `v10.5.99` → `versionCode = 100599`

**Rule**: `versionCode` must always increase (Google Play requirement). This formula ensures that any new semantic version has a higher `versionCode`.

### Creating a Release

1. **Prepare your changes** and commit to `main`
   ```bash
   git add .
   git commit -m "Prepare release v1.0.0"
   git push origin main
   ```

2. **Create and push a version tag**
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```

3. **GitHub Actions automatically**:
   - Extracts version `1.0.0` from tag
   - Sets `versionCode=10000`, `versionName="1.0.0"`
   - Builds and uploads APK to Google Play
   - Creates GitHub Release

4. **Verify** in Google Play Console → Internal Testing track

### Hotfixes

For critical bug fixes:

```bash
# Create hotfix branch
git checkout -b hotfix/critical-bug

# Fix the bug and commit
git add .
git commit -m "Fix critical bug"

# Merge to main (via PR or direct push)
git push origin hotfix/critical-bug
# → Create PR, get review, merge

# Tag and release
git tag v1.0.1
git push origin v1.0.1
```

GitHub Actions will deploy automatically.

### Rollback

If a release has critical issues:

```bash
# Revert the problematic commit(s)
git revert <commit-sha>
git push origin main

# Create new tag with higher version
git tag v1.0.2
git push origin v1.0.2
```

**Note**: You cannot "undo" a Google Play release; always roll forward with a higher version.

## Manual Release (Without Tag)

If you need to deploy without creating a tag:

1. Go to repo → Actions → "Release to Google Play"
2. Click "Run workflow" dropdown
3. Select `main` branch
4. Click "Run workflow"

**Note**: Manual dispatch uses the latest git tag for versioning, or `0.1.0` if no tags exist.

## Testing the Workflows

### Test CI Workflow

1. Create a test branch
   ```bash
   git checkout -b test-ci
   ```

2. Make a trivial change
   ```bash
   echo "# Test CI" >> README.md
   git add README.md
   git commit -m "Test CI workflow"
   git push origin test-ci
   ```

3. Create a PR
   - Go to GitHub → Pull Requests → New PR
   - Select `test-ci` → `main`
   - Watch CI workflow run

4. **Expected result**: ✅ Lint passes, ✅ Tests pass, ✅ Debug APK builds

### Test Release Workflow (Dry Run)

**Option 1: Manual dispatch**
1. Go to Actions → "Release to Google Play"
2. Click "Run workflow" → Select `main` → Run
3. Watch the workflow (will fail at upload step if Google Play setup incomplete)

**Option 2: Create a test tag**
```bash
git tag v0.1.0-test
git push origin v0.1.0-test
```

Watch the workflow. It will:
- ✅ Build the APK
- ✅ Run tests/lint
- ❌ Upload to Google Play (fails until setup complete)

**Delete test tag after**:
```bash
git tag -d v0.1.0-test
git push origin :refs/tags/v0.1.0-test
```

## Troubleshooting

### "No tags found" error

**Symptom**: Workflow fails or version defaults to `0.1.0`

**Fix**: Ensure you've created at least one tag
```bash
git tag v1.0.0
git push origin v1.0.0
```

### "Keystore file not found"

**Symptom**: Build fails with "Keystore file 'release.keystore' not found"

**Fix**:
1. Verify `KEYSTORE_BASE64` secret is set correctly
2. Ensure base64 encoding was done correctly:
   ```bash
   base64 -i tidewatch-upload.keystore > keystore.txt
   # Copy contents of keystore.txt to GitHub secret
   ```

### "Upload failed: Unauthorized"

**Symptom**: Upload to Google Play fails with 401/403

**Fix**:
1. Verify service account JSON is correct
2. Check service account has "Create releases" permission
3. Ensure app exists in Google Play Console
4. Verify `packageName` in workflow matches app ID in Play Console

### "Version code already used"

**Symptom**: Upload fails because version code exists

**Fix**: Create a new tag with higher version (e.g., `v1.0.1`, `v1.1.0`)

Google Play requires strictly increasing version codes.

### Tests fail in CI but pass locally

**Symptom**: CI shows test failures but `./gradlew test` passes locally

**Fix**:
1. Ensure local git is clean: `git status`
2. Try running with `--no-daemon`: `./gradlew test --no-daemon`
3. Verify Java version: `java -version` (should be 17)
4. Clear build cache: `./gradlew clean`

## Security Best Practices

### GitHub Secrets
- ✅ Never commit keystore files or passwords
- ✅ Use GitHub Secrets for all sensitive data
- ✅ Rotate service account keys annually
- ✅ Limit service account permissions (principle of least privilege)

### Keystore Management
- ✅ Back up keystore file securely (password manager, encrypted storage)
- ✅ **If you lose the keystore, you cannot update the app** (must publish as new app)
- ✅ Google Play App Signing protects against this (can re-generate upload key)
- ✅ CI workflows clean up keystore after use

### Branch Protection
Consider enabling on `main` branch:
- Require PR reviews before merging
- Require CI workflow to pass
- Restrict who can push/merge

## Monitoring and Feedback

### GitHub Actions UI
- Go to repo → Actions to see workflow runs
- Check logs for each step
- Download artifacts (lint reports, test results)

### Google Play Console
- Go to your app → Release → Internal Testing
- See version history and rollout status
- Monitor crash reports and reviews

## Future Enhancements

Possible improvements (out of scope for initial setup):
- Automated changelog generation from commit messages
- Slack/Discord notifications for deployments
- Screenshot tests and visual regression testing
- Automated promotion from internal → beta → production
- APK size tracking over time
- Crash reporting integration

## References

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Google Play Publishing API](https://developers.google.com/android-publisher)
- [App Signing Guide](https://developer.android.com/studio/publish/app-signing)
- [Semantic Versioning](https://semver.org/)
- [upload-google-play Action](https://github.com/r0adkll/upload-google-play)
