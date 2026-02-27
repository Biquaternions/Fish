
# Compatibility layer

Since I don't have a huge userbase to test Fish, I usually scrape for
problems/incompatibilities on a project that also provides PWT and has a larger userbase, Leaf.

Fish provides a compatibility layer based on a single principle: \
_If I'm going to make my plugin work with PWT, in this part, I will just call the sync scheduler on the conflicting API_

And I have two rules to determine what API should have this compatibility layer:
1. The API is expected to work asynchronously on Paper
2. The compatibility layer doesn't fundamentally break the API


However, there are two (2) exceptions to this rule:

1. **[BetterTeams](https://github.com/booksaw/BetterTeams/blob/11ca5a8b24e07dcd15fffe59ec9b745877755834/BetterTeams/src/main/java/com/booksaw/betterTeams/events/MCTeamManagement.java#L135) 5.0.0 calling CraftTeam#addEntry async** \
   A plugin I found in [Leaf discord](https://discord.com/channels/1145991395388162119/1420747109409230988/1420747137775566939).
   While I'm not the biggest fan of allowing Team#addEntry to be called async, as it accesses HashMaps and HashSets that
   are not thread-safe; I haven't found any issues related to `ConcurrentModificationException` in their repository and,
   therefore will allow `CraftTeam#addEntry` and `CraftTeam#removeEntry` to have the compatibility layer.
2. **Frost 1.6.1-r calling CraftTeam#setPrefix async** \
   A plugin I've been testing. While this seems to be a bug on the plugin's end, the lack of reports related to
   `ConcurrentModificationException` and its nature made it impossible to re-create by myself in both Paper and Fish. \
   Given that `CraftTeam` had already two method with similar properties (accessing HashMaps and HashSets) that were
   granted the compatibility layer, I decided to grant it to all related methods as well.

In the case of BetterTeams I genuinely have no idea why it has worked so far without breaking in Paper. \
While `Object2ObjectOpenHashMap` lacks `ConcurrentModificationException` validations, `HashSet` has those... and is accessed
immediately right after `Object2ObjectOpenHashMap` is... I'm very confused. \
The `HashSet` gets called rarely and usually gets copied before doing something on it, but still gets called sometimes...
as mentioned, I don't fully understand why it has worked so far asynchronously in Paper.

