# Testing Chrome PWA File Attachment

This guide will help you test the Chrome PWA file attachment use case using the IntentMaster module.

## Overview

By default, Chrome's Progressive Web Apps (PWAs) can only attach image, audio, or video files. This limitation is hardcoded in Chrome when it sends the `GET_CONTENT` intent. The IntentMaster module can modify this intent to allow any file type to be attached.

## Setup

1. Install the IntentMaster module and enable it in LSPosed Manager
2. Add Chrome to the target apps in IntentMaster settings
3. Add the following rule:

```json
{
  "name": "Chrome PWA File Attachment Enabler",
  "enabled": true,
  "packageName": "com.android.chrome",
  "action": "android.intent.action.GET_CONTENT",
  "type": "image/.*|video/.*|audio/.*",
  "intentAction": "MODIFY",
  "modification": {
    "newType": "*/*"
  }
}
```

## Testing

1. Install a PWA with the Web Share Target API capability
   - You can use https://scrapbook-pwa.web.app/ for testing
   - Open it in Chrome and install it as a PWA (⋮ > Install app)

2. Open the installed PWA
   - It should open in standalone mode without browser UI

3. Try to attach a file
   - Look for an "Add" or "Attach" button in the PWA
   - For scrapbook-pwa, click the "+" button to add content

4. Without the IntentMaster rule, only images, videos, and audio would be available
   - With the rule enabled, you should see all file types, including PDFs, documents, etc.

5. Try attaching a PDF or other non-media file type
   - The file should be successfully attached
   - The PWA should process it according to its capabilities

## How It Works

1. When a PWA tries to get content, Chrome creates an intent with action `android.intent.action.GET_CONTENT`
2. Chrome sets the MIME type to be limited to media types (`image/*`, `video/*`, `audio/*`)
3. The IntentMaster module intercepts this intent before it's sent
4. The module matches the intent against our rule and modifies the MIME type to `*/*` (all file types)
5. The modified intent is passed to the system, showing the file picker with all file types available

## Troubleshooting

If you encounter issues:

1. Make sure IntentMaster is properly enabled
2. Verify the rule is added correctly and enabled
3. Check the IntentMaster logs to see if the intent was intercepted
4. Some PWAs might have additional client-side validation that restricts file types

## Additional Notes

- This technique works for any PWA that uses the Web Share Target API
- The same approach can be used to modify other aspects of intents in Chrome or other apps
- Some PWAs might handle file attachments differently, this guide focuses on the standard Web Share Target approach 