# ChangeLogs
## v26.1.0.10
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

## v26.1.0.9
* Added HDR Support

## v26.1.0.8
* Bump up ldlib2
* Improved APIs
* Added node descriptions
* Added Gate Check for iris format compat

## v26.1.0.7
* Bump up ldlib2

## v26.1.0.6
* Added geometry nodes
* Added iris compat

## v26.1.0.5
* Added preview mode persisted
* Added shader gradient support

## v26.1.0.4
* Added Light Ubo + Global Ubo
* Added more tooltips
* Removed unused tests

## v26.1.0.3
* Fixed wire portal
* Emit exec-flow ports before data ports
* Fixed ScreenPosition

## v26.1.0.1
init