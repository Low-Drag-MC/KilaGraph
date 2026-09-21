# ChangeLogs
## v21.1.0.15
* Added quaternion type and rotation nodes (axis angle, euler, from-to, compose, inverse, slerp, rotate vector, angle between, Vec4 bridge)
* Added vector nodes (direction to, set length, slerp, perpendicular, wrap, Vec2/Vec3/Vec4 conversions)
* Added math nodes (wrap, snap, step, smoothstep, inverse lerp, delta angle, move towards, nearly equal, wave)
* Added a per-node declaration of whether a graph may run it off the game thread
* Added a variable store access API
* Added a Set Var that points at a variable's declaration instead of at its spelling
* Added setSampler, binding a Sampler2D value's params and not only its texture
* Fixed a node's declared ports landing below the dynamic ones it defines
* Fixed a port with no accessor losing the editor its own type registered
* Fixed a loaded pin's constant keeping the type it was saved under instead of the pin's declared type

## v21.1.0.14
* Fixed a variable with a null default crashing
* Added the exec pin a node was entered through
* Added retainExecOutputs for exec node output cache
* Added retainExecOutputs

## v21.1.0.13
* Refactored Vector nodes of blueprint
* Added tangent space support to the shader graph
* Added the missing node wiki entries for ports and options

## v21.1.0.12
* Fixed whole numbers exact in math, compare, bitwise and convert nodes
* Fixed collection ports the canonical LIST handle instead of the bare class
* Improved one prepared graph be executed by several threads

## v21.1.0.11
* Added LDLib2 UI nodes (element, style, stylesheet, animation, event, drag, sync, binding, rpc, xml, template, context)
* Added multi-line text node
* Improved node library grouping (mc and ui parent groups)

## v21.1.0.10
* Added Minecraft nodes (entity, player, level, block, item, container, redstone, enchantment, potion, recipe, loot, mining, tag, nbt, text, regex)
* Added info context nodes
* Added vector types and vector nodes
* Improved blueprint execution performance
* Improved subgraph host context
* Unified mc_ node prefix
* Fixed breakpoints on loop, sequence and subgraph entry nodes

## v21.1.0.9
* Added HDR Support

## v21.1.0.8
* Added node description

## v21.1.0.7
* Added geometry nodes
* Improved camera data
* Improved APIs

## 21.1.0.5
* Added preview mode persisted
* Added shader gradient support

## 21.1.0.4
* Added Light Ubo + Global Ubo
* Added more tooltips
* Removed unused tests

## 21.1.0.3
* Fixed wire portal
* Emit exec-flow ports before data ports
* Fixed ScreenPosition

## 21.1.0.1
init