<div align="center">

## 🐟 Fish 🐟
Fish is a [Paper](https://github.com/PaperMC/Paper)/[Pufferfish](https://github.com/pufferfish-gg/Pufferfish/) fork designed for **performance**.


</div>

<div align="center">

### This project is in very EARLY stages of development, no builds will be provided.
### If you use it anyway feedback will be appreciated.

</div>

## Usage
For Fish config file `fish.yml`, startup flags and development API, check [USAGE.md](USAGE.md).

## Idea
Save some performance using the best (and less breaking) patches from other forks until we learn enough knowledge on Minecraft internals to make our own patches.

## License
All patches are licensed under the MIT license.

[![MIT License](https://img.shields.io/github/license/PurpurMC/Purpur?&logo=github)](LICENSE)

See [PaperMC/Paper](https://github.com/PaperMC/Paper), and [PaperMC/Paperweight](https://github.com/PaperMC/paperweight) for the license of material used by this project.

## Building and setting up

#### Initial setup
First, <u>clone</u> this repository. Do not download it.

Then run the following command in the root directory:

```
./gradlew applyAllPatches
```

The project is now ready for use in your IDE.

#### Creating a patch

See [CONTRIBUTING.md](CONTRIBUTING.md).

#### Compiling

Use the command `./gradlew build` to build the API and server. Compiled JARs
will be placed under `fish-api/build/libs` and `fish-server/build/libs`.
**These JARs are not used to start a server.**

To compile a server-ready paperclip jar, run `./gradlew createMojmapBundlerJar`.
To install the `fish-api` and `fish` dependencies to your local Maven repo, run `./gradlew publishToMavenLocal`. The compiled paperclip jar will be in `fish-server/build/libs`.

# Support
This project is designed for Serlith Network, so support is currently not guaranteed until we can find someone to properly maintain the project.

# Credits:

1. PaperMC Team.
2. Pufferfish Host.
3. PurpurMC Team, for their paperweight project setup and both Inventory and Villager patches.
4. Winds-Studio, for their async and performance patches.
