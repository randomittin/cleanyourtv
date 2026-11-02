# Minimal icon pack

A small icon pack in the standard ADW/Nova format: a muted rounded square with one white letter for twelve
common TV apps (Netflix, YouTube, Prime Video, Disney+, Hotstar, Apple TV, Spotify, Plex, Kodi, VLC, Play
Store, Settings). The art is our own and tiny (vector drawables, a few hundred bytes each); it contains no
brand logos or trademarks. A launcher that reads icon packs can use it, and a private pack with your own
pictures is built from the same folder (below). Separate from the launcher, and it has no screen or card:
it is only read by the launcher.

Package `com.gathin.iconpack.minimal`. You see it as **Minimal icon pack**. Opening it shows a short message
("Minimal icon pack: 12 icons") and closes.

## How a launcher finds it

The activity answers `org.adw.launcher.THEMES` and `com.novalauncher.THEME`, the two intents icon-pack
aware launchers query, and `res/xml/appfilter.xml` maps each app to a drawable
(`<item component="ComponentInfo{package/activity}" drawable="ic_package"/>`). Nothing else connects them.

## Build and install

JDK 17 and Android SDK 34, as for the launcher. The `gradlew` scripts reuse the launcher's pinned Gradle
wrapper with this folder as the project.

```sh
cd addons/icon-pack-minimal && ./gradlew --no-daemon assembleRelease && cd ../..
TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/tv.py --push addons/icon-pack-minimal/app/build/outputs/apk/release/app-release.apk /data/local/tmp/addon.apk
TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/tv.py 'pm install -r /data/local/tmp/addon.apk; rm -f /data/local/tmp/addon.apk'
```

Windows: `gradlew.bat assembleRelease` in `addons\icon-pack-minimal`, as in [`addons/README.md`](../README.md).

## Which apps

`components.txt` lists one `package/activity` per line and an optional label whose first letter becomes the
monogram. The activity names were seen on Android TV and Google TV boxes and vary by app version and TV, so an
app that does not match keeps its own icon. To find the right name for an app on your TV:

```sh
adb shell cmd package resolve-activity --brief -c android.intent.category.LEANBACK_LAUNCHER <package>
```

After editing the list, regenerate and check (Python 3, standard library only):

```sh
python3 addons/icon-pack-minimal/make-pack.py          # writes drawables and appfilter.xml
python3 addons/icon-pack-minimal/make-pack.py --check  # exit 0 when the files match components.txt
```

## Build a private pack from your own pictures

Brand logos are never committed here. If you want them, build a pack on your own machine from your own
PNGs:

1. Copy this folder somewhere private, outside the repository (`cp -R addons/icon-pack-minimal ~/my-pack`),
   and give it its own name so it installs next to this one.
2. Make a folder of pictures named after the package: `icons/com.netflix.ninja.png`,
   `icons/com.google.android.youtube.tv.png` (`.webp` and `.jpg` work too; square, 256x256 or larger).
3. Add a `package/activity` line to `components.txt` for every app you have a picture for (a picture with
   no line is skipped with a message).
4. Run `python3 make-pack.py --icons icons`. Each picture is copied to
   `app/src/main/res/drawable-nodpi/ic_<package>.png`, replaces that app's monogram, and `appfilter.xml` is
   regenerated; apps without a picture keep the monogram. Run it again without `--icons` to go back.
5. Build with your own application id: `./gradlew --no-daemon assembleRelease -PpackApplicationId=com.example.mypack`,
   and install `app/build/outputs/apk/release/app-release.apk` as above.

The pictures folder is read only when you pass `--icons`. Keep the copy out of git: the repository's
tests refuse picture files in this folder.

## Tests

```sh
python3 -m unittest tests.test_icon_pack_minimal
```

## Permissions

None. The manifest has no `uses-permission`, no network access, no service and no receiver; the only
component is the exported activity that answers the two icon-pack intents.

Tested on: Skyworth Android TV 10 (SWTV-20AE), with the launcher's icon_pack switch. Netflix, YouTube, Prime Video, Hotstar, Apple TV and Play Store matched.
