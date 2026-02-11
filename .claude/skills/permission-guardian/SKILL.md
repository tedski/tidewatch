---
name: permission-guardian
description: Monitor permission changes and ensure proper runtime permission handling patterns are followed in the TideWatch WearOS app
user-invocable: true
---

# Permission Guardian

**Invoke with**: `/permission-guardian [feature description]`

## Purpose

Monitor permission changes and ensure proper runtime permission handling patterns are followed in the TideWatch WearOS app.

## Baseline Patterns (Current Codebase)

### Approved Permissions
- `WAKE_LOCK` - For maintaining screen-on during active use
- `ACCESS_COARSE_LOCATION` - For finding nearby tide stations

### Permission Request Pattern
```kotlin
// Activity Result API with rememberLauncherForActivityResult
val launcher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
) { isGranted ->
    // Handle permission result
}
```

### Permission Check
```kotlin
ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
```

### UI Pattern
- Show clear rationale explaining why permission is needed
- Provide fallback mode if permission denied (e.g., "Browse All Stations" vs "Nearby Stations")
- Never block core functionality on optional permissions

## Checking Process

When a feature is proposed or implemented:

1. **Scan AndroidManifest.xml**
   - Check for new `<uses-permission>` declarations
   - Identify dangerous permissions (location, camera, sensors, etc.)

2. **Verify Runtime Checks**
   - Confirm dangerous permissions have runtime checks (API 23+)
   - Ensure `checkSelfPermission()` before attempting protected operations

3. **Review Rationale UI**
   - Check for user-facing explanation of permission benefit
   - Verify clear, non-technical language
   - Ensure timing (shown before or after first denial)

4. **Validate Fallback Behavior**
   - Confirm app remains functional if permission denied
   - Check for graceful degradation
   - Verify no crashes or blank screens

## Warning Triggers

⚠️ **New Permission**: Any addition to AndroidManifest.xml
⚠️ **Missing Runtime Check**: Dangerous permission without `checkSelfPermission()`
⚠️ **No Rationale**: Permission request without user explanation
⚠️ **No Fallback**: Feature completely unusable if permission denied
⚠️ **Scope Creep**: Permission broader than needed (e.g., FINE_LOCATION when COARSE suffices)

## Output Format

```markdown
## Permission Guardian Analysis

### ✅ Compliant Patterns
- [List of things following established patterns]

### ⚠️ Warnings
- **Issue**: [Description of potential problem]
  - **Pattern**: [What the codebase currently does]
  - **Proposed**: [What's being introduced]
  - **Impact**: [Why this matters for users/privacy]
  - **Recommendation**: [How to align with patterns]

### 📊 Summary
[Overall assessment and approval/concerns]
```

## Example Analysis

**Feature**: "Add compass overlay showing direction to station"

### ✅ Compliant Patterns
- (None - no existing pattern for sensors)

### ⚠️ Warnings
- **Issue**: New sensor permission required
  - **Pattern**: App currently only uses WAKE_LOCK and COARSE_LOCATION
  - **Proposed**: Adding BODY_SENSORS or similar for compass
  - **Impact**: Increases permission footprint, potential privacy concern
  - **Recommendation**: Consider using device orientation API which may not require explicit permission, or make compass purely optional with clear rationale

### 📊 Summary
New permission proposed. Ensure runtime check, rationale UI, and fallback to static station display.
