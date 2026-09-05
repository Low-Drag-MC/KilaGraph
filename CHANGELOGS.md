# ChangeLogs
## v26.1.0.14
* Fixed a variable with a null default crashing
* Added the exec pin a node was entered through, and the Do Once, Do N, Flip Flop, Multi Gate and Toggle Gate nodes
* Added retainExecOutputs for exec node output cache
* Added object transform API slots
* Fixed DynamicTransforms being declared on pipelines that never bind it

## v26.1.0.13
* Refactored Vector nodes of blueprint
* Added tangent space support to the shader graph
* Added the missing node wiki entries for ports and options

## v26.1.0.12
* Fixed whole numbers exact in math, compare, bitwise and convert nodes
* Fixed collection ports the canonical LIST handle instead of the bare class
* Improved one prepared graph be executed by several threads

## v26.1.0.11
* Added LDLib2 UI nodes (element, style, stylesheet, animation, event, drag, sync, binding, rpc, xml, template, context)
* Added multi-line text node
* Improved node library grouping (mc and ui parent groups)

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