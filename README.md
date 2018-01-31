# Jenkins build scripts

## lineage-targets.json

Seeds the job for lineage-build-kicker, which loops over the device entries and spawns lineage-build jobs for each. The parameters here line up with the variables passed to the bash stages in lineage-build

Parameters:

| Parameter | Values/Examples | Description | Default |
| --------- | --------------- | ----------- | ------- |
| `device` | `angler`, `bullhead`, `marlin` | device codename for the device to build. These are typically all lowercase and usually bear no relation to the consumer name for the device | `HELP-omgwtfbbq` (will fail the build) |
| `version` | `14.1`, `13.0`, `11` | version of LineageOS to build | `14.1` |
| `build_type` | `user`, `userdebug`, `eng` | the type of build to make. See the differences in the [Android docs](https://source.android.com/source/add-device#build-variants) | `userdebug` |
| `ota` | `true`, `false` | If true, the build is uploaded to [OTA server](https://lineage.harryyoud.co.uk), where it can be distributed over the air to devices | `true` |
| `repopick_nums` | `157953,172537` | Comma separated list of changes to download from the [LineageOS Gerrit](https://review.lineageos.org) | (blank) |
| `repopick_tops` | `network-traffic,long-press-power-torch-timeout` | Comma separated list of topics to download from the [LineageOS Gerrit](https://review.lineageos.org) | (blank) |
| `with_su` | `true`, `false` | If true, the build will be pre-rooted with the LineageOS root solution. If not, a root package can be downloaded from [LineageOS](https://download.lineageos.org/extras) | `false` |
| `with_dexpreopt` | `true`, `false` | If true, some apps are pre-optimised during the build. This dramatically increases build time and RAM usage during the build, but decreases the time spent on the first boot after an OTA update | `false` |
| `signed` | `true`, `false` | If true, builds will be signed with the keys in the directory specified in `lineage-14.1`. Beware, moving between non-signed and signed builds requires a factory reset or [migration build](https://wiki.lineageos.org/signing_builds.html#changing-keys) | `false` |
| `signed_backuptool` | `true`, `false` | If `signed` is true, and this is false, backuptool will not be included in the build, allowing `/system` to be left untouched to preserve verity | `true` |

## lineage-build-kicker

This downloads and interprets the json targets file, syncs the mirror (and waits for this to complete), and spawns the `lineage-build` jobs with the parameters above. If they aren't specified, the defaults are assumed.

If it is given a `device` parameter (eg, when launched manually), it loops over the JSON until reaching the device wanted, and spawns `lineage-build` for it.
