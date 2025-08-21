
# Rules

## 1. Parallel World Ticking is not free-for-all performance
1. If you only have the default minecraft worlds, this feature is **completely worthless**.
2. Players have to be **decently distributed** across multiple worlds for this feature to be effective.
3. If you already started your server, **players will not move to a different world** even if you beg them to do so.
4. Stress is independent for each world, but **lag is not**. If one of the worlds exceeds its tick boundary (50 mspt), **all worlds will have to wait**.

## 2. Datapacks are the source of all evil
1. Never use datapacks in a Parallel World Ticking server.
2. Datapacks not only kill performance in any server software, they also schedule tasks outside the ticking world and will cause crashes.
3. If you fork this project, scheduling datapack's command execution as tasks is not a solution since datapacks can easily spam hundreds of commands which (if unlucky) can easily overflow the task queue.

## 3. Parallel World Ticking is not 100% compatible with Bukkit
1. **YOU** are the one that has to design the server around Parallel World Ticking, not ask the feature to be compatible with your server.
2If a plugin doesn't work, look for a different one.

## 4.Minimize async accesses
1. Use the key `log-async-accesses: true` in Fish config to print a stacktrace every time a plugin tries to access a protected function async.
2. While this function will be properly scheduled to be executed safely, this means an additional burden for the server, which means, you show minimize this accesses.

How to use:
```yaml
async:
    world-ticking:
        enabled: true
        threads: 8
        log-async-accesses: true
```

## 5. If you're a dev, make your plugins compatible with Parallel World Ticking, not the other way around
1. Make proper use of Paper's API, don't try making write operations async.
2. Avoid calling protected functions.
3. The key is i-know-what-i-am-doing-i-swear-by-fish.
4. Never schedule tasks and wait in the main/world thread.

## 6. Avoid incompatible plugins
The following list contains plugins that are known to be incompatible. \
It is very likely that you'll find more plugins like these. If you were to find a plugin that is incompatible,
I highly encourage you to just not use it and look for an alternative. \
Please, test the server extensively before releasing to the public, specially operations that involve teleportation across worlds,
requesting block/entity information in another world, etc. \
You can always report bugs in the [Issues tab](https://github.com/Biquaternions/Fish/issues) as there might be a chance that is
something that can be fixed on Fish end, or in case it isn't, it will help expand this incompatible list:
1. Denizen (will cause a deadlock 100%)
2. Skript (will cause a deadlock probably)
3. Citizens (if NPCs teleport to a different world... gg)
4. NoCheatPlus (Better read [Sparkly's note](https://github.com/SparklyPower/SparklyPaper/blob/ver/1.21.8/docs/PARALLEL_INCOMPATIBLE_PLUGINS.md))

You have read the entire rules, you shall be allowed to use Parallel World Ticking. \
Replace `<the-key-here>` with the real key (which you should've found if you read the whole document) in the `world-ticking` section of Fish's config:

```yaml
async:
    world-ticking:
        enabled: true
        threads: 8
        <the-key-here>: true
```
