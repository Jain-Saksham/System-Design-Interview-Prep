# Search Autocomplete: Interview Questions

**1. Estimate the query load.**
Assume 10M DAU × 10 searches × ~6 suggestion requests per search (with debouncing) = 600M/day ≈ **7k QPS** average, maybe 20k+ at peak. Caching removes a large share of this before it reaches servers.

**2. Why a trie?**
All completions of a prefix live in one subtree, and finding the prefix node takes O(prefix length). Hash maps can't answer "everything starting with jav" without scanning every key.

**3. Isn't walking the subtree to find top-k slow?**
Yes, for short prefixes ("a" has millions of descendants). So precompute and store the top-k list at every node at build time. The lookup is then O(prefix length) plus returning k items.

**4. What's the memory cost of storing top-k at each node?**
Each query appears in the top-k lists of up to (length) nodes, so storage grows roughly k× over a plain trie. Mitigations: store references or IDs instead of strings, prune queries below a count threshold, cap stored depth.

**5. How do new searches update suggestions?**
Not in real time. Searches are logged, aggregated per window (e.g., hourly), and a new trie is built offline and hot-swapped into servers. Updating the live trie per search would need locking on the read path and recomputing top-k up the tree.

**6. How do you swap in a new trie without downtime?**
Build the full new trie (or load it from a snapshot) in the background, then switch one volatile reference. In-flight requests finish on the old trie; new ones use the new trie. Old versions stay available for rollback.

**7. How do you rank suggestions?**
Base: search frequency with time decay (recent searches weigh more). Extensions: location, language, personalization (re-rank the top 20 global candidates per user), and freshness boosts for trending queries.

**8. How do you remove an offensive suggestion urgently?**
Add it to the block list. The next build excludes it, and for immediate effect the serving layer also checks a small, frequently refreshed block list before responding. Purge cached responses for affected prefixes from the CDN.

**9. What caching layers would you use?**
Browser cache (the same user retyping), CDN/edge cache keyed by prefix (popular short prefixes), and the in-memory trie itself. Results change only on rebuild, so long TTLs are safe.

**10. How would you scale beyond one machine's memory?**
Shard by prefix range (`a–f`, `g–m`, ...), with a routing layer that picks the shard from the first characters. Watch for skew: "s" and "c" are much bigger than "x", so split hot ranges further, or shard by hash of the first two or three characters.

**11. How do you support trending queries (e.g., breaking news)?**
A small streaming job (Flink) counts queries over the last few minutes and flags spikes relative to baseline. The serving layer merges these trending candidates with the hourly trie's suggestions.

**Likely follow-ups:** typo tolerance (edit-distance search or n-gram index) · multi-word matching ("york new" → "new york") · how Google-style personalization affects caching · supporting multiple languages and scripts.
