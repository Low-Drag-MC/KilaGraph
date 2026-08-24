## v26.1.0.11
* Added LDLib2 UI nodes (element, style, stylesheet, animation, event, drag, sync, binding, rpc, xml, template, context)
* Added multi-line text node
* Improved node library grouping (mc and ui parent groups)
* Added Minecraft nodes (entity, player, level, block, item, container, redstone, enchantment, potion, recipe, loot, mining, tag, nbt, text, regex)
* Added info context nodes
* Added vector types and vector nodes
* Improved blueprint execution performance
* Improved subgraph host context
* Unified mc_ node prefix
* Fixed breakpoints on loop, sequence and subgraph entry nodes
* Container pins are now ResourceHandler; removed Container Set (26.1 has no usable overwrite operation)
* World Bounds reports minY/maxY per 26.1; Comparator Output gained a side input
* Removed the exhaustion output from Player Food (no longer readable in 26.1)