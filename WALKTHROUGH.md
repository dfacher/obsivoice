# Verification Walkthrough

Follow these steps to verify ObsiVoice is working correctly.

## 1. Initial Setup
1. Launch **ObsiVoice**.
2. You should see the "Welcome" setup screen.
3. Tap **Go to Settings**.
4. Paste your **OpenAI API Key**.
5. Tap **Select 'journals' Folder**.
   - Navigate to `Documents/df/journals`.
   - Tap **Use this folder** (or "Allow").
6. Tap **Save & Close**.
7. Grant **Microphone** and **Notification** permissions when prompted.

## 2. Test Recording
1. Tap the big red/blue **Record** button.
2. Verify the button creates a pulse animation.
3. Verify a notification appears: "ObsiVoice: Recording voice note...".
4. **Press Home** to minimize the app.
5. Speak a test note: *"This is a test note about productivity and self-improvement. The app should transcribe this and save it to my journal."*
6. Tap the **Notification** -> "Stop" action (or open app and tap Stop).

## 3. Verify Output
1. The notification should change to "Transcribing and analyzing...".
2. Wait 5-10 seconds.
3. Open **Obsidian** (or a file manager).
4. Navigate to `Documents/df/journals`.
5. Look for a new file named like `2024-12-08_203000_voice-note.md`.
6. Open it and check:
   - **Frontmatter**:
     ```yaml
     created:
       "2024-12-08 20:30":
     tags:
       - journal
       - Productivity
       - Self-improvement
     topic: A test note about productivity...
     processed: false
     ```
   - **Body**: The accurate transcription of what you said.

## 4. Test Widget
1. Go to your Android Home Screen.
2. Long press -> Widgets -> ObsiVoice.
3. Drag the microphone widget to your home screen.
4. Tap the widget.
5. Verify recording starts immediately (notification appears).
6. Tap the notification to stop.
