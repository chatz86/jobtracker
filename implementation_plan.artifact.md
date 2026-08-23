# Fix Job Saving and Calendar Visibility

The user reports that jobs are not being saved as events or are not visible. Analysis shows several issues:
1.  **Incomplete Saving Flow:** "Job" entries are only saved if the voice dictation for patient details is completed. If the voice recognizer is cancelled or fails, the entry is never saved to the calendar.
2.  **Calendar Selection Logic:** The app picks the first calendar it finds if a specific "health.wa.gov.au" account isn't found. This might be a hidden or non-syncing local calendar.
3.  **Missing Network Sync:** The `WEBHOOK_URL` in `Configuration.kt` is never used, and the `INTERNET` permission is missing. If the user expects these to show up in OneNote, this is why they can't see them.
4.  **UI Feedback:** There's no way to skip voice prompts if the user is in a noisy environment, leading to "stuck" entries.

## User Review Required

> [!IMPORTANT]
> The app currently requires completing voice prompts for patient details to save a "Job". I will add a "Skip" option so jobs can be saved even without these details.
> I will also implement the OneNote sync via the provided Webhook URL. This will require the `INTERNET` permission.

## Proposed Changes

### Configuration & Manifest
#### [MODIFY] [AndroidManifest.xml](file:///home/chat/AndroidStudioProjects/JobTracker/app/src/main/AndroidManifest.xml)
- Add `android.permission.INTERNET` and `android.permission.ACCESS_NETWORK_STATE`.

### Presentation Layer
#### [MODIFY] [MainActivity.kt](file:///home/chat/AndroidStudioProjects/JobTracker/app/src/main/java/com/example/jobtracker/presentation/MainActivity.kt)
- **Improve `saveToCalendar`:**
    - Query for `VISIBLE` and `IS_PRIMARY` calendars.
    - Add more logging to help diagnose which calendar is being used.
- **Implement `syncToWebhook`:**
    - Add a function to send the job/patrol data to the `WEBHOOK_URL` using `HttpURLConnection` in a coroutine.
- **Update UI states:**
    - Add a "Skip/Manual" button during `VOICE_PATIENT_NAME` and `VOICE_PATIENT_ID` states.
    - Ensure `saveToCalendar` and `syncToWebhook` are called whenever an entry is finished, regardless of whether voice was used.

## Verification Plan

### Automated Tests
- I'll check if the code compiles with the new network calls.

### Manual Verification
- Deploy to a device (if available) and verify:
    - Jobs can be finished and saved even if voice is cancelled.
    - Check logs for "Event saved successfully" and "Sync successful".
    - Verify that a valid calendar ID is being selected.
