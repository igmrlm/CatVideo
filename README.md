# CatVideo Concatenator

CatVideo Concatenator is a simple Android app written in Kotlin that allows users to **select multiple videos from their device** and concatenate them into a single video without transcoding. The app displays **real-time text-based progress**, preserves the original video orientation, and saves the output video to the device's camera roll.

---

## Features

- Select multiple videos from the device storage.
- Concatenate videos **without re-encoding** (fast and preserves original quality).
- Display **progress updates**: percentage complete, free space remaining, and estimated time left.
- Corrects **video orientation** so the output is not upside-down.
- **Screen rotation is disabled** during processing to prevent crashes.
- Optionally, users can support the developer via a clickable **Ko-fi link** in the app.

---

## How It Works

1. The app lets the user select one or more videos from their device.
2. Videos are processed on a **background thread** using Android's `MediaExtractor` and `MediaMuxer`.
3. The app concatenates the video and audio tracks sequentially, adjusting **timestamps** to ensure smooth playback.
4. Progress is reported in real-time to a `TextView`, including:
   - Completion percentage
   - Free space on the device
   - Estimated time remaining
5. The final concatenated video is saved in the device's `Movies` directory and is visible in the camera roll.

---

## Basic Instructions

1. Tap the **“Select Videos”** button.
2. Choose multiple video files from your device.
3. The app will display concatenation progress in real-time.
4. Once complete, the output video is saved to your **camera roll**.

---

## Use Cases

- Combine clips from an event into a single video.
- Merge multiple short recordings into one file for sharing.
- Quickly compile video highlights without quality loss.
- Export HDR videos without a HDR editing program

---

## Caveats & Notes

- The app **does not transcode**; all videos must have **compatible codecs** (e.g., H.264 video and AAC audio). Mixing incompatible formats may cause errors.
- The app assumes all videos have the **same rotation**. If videos have different orientations, some clips may appear sideways.
- Screen rotation is locked during processing to avoid crashes.
- The app only handles videos accessible via the system **document picker** (does not scan all internal storage automatically).
- Large files may take time depending on device performance and storage speed.
- Free space is reported in MB; ensure enough storage is available for the combined video.

---

## Development Notes

- Written in Kotlin for Android using `MediaExtractor` and `MediaMuxer`.
- Background processing is done using a simple `Thread`.
- Output files are saved in `Movies` via `MediaStore` for immediate visibility in gallery apps.
- Progress is reported to the main thread using `runOnUiThread`.

---

## License

This project is **open-source**. Feel free to fork, modify, or contribute.

---

## Support

If you like this app, you can support the me via [Ko-fi](https://ko-fi.com/nathanaelnewton).

---
