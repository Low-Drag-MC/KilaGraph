## v21.1.0.16
* Added a nearest-entity node
* Added the damage source as a graph type, with nodes to read one, test it against a damage type tag, build one and hurt with it
* Added NBT Copy, and a copy pin on NBT Set, NBT Path Set and NBT Remove (they still write in place by default)
* Added colour to vector conversion: a colour wired into a vector pin reads as its red, green and blue
* Changed a pure node to be worked out again for each exec node that reads it, as Unreal does
* Fixed loops, subgraph returns and reads after a flow not seeing the last write; For and ForEach re-read their count and list before each iteration
* Fixed an exec node's published outputs being lost when an output it did not publish is read
* Improved port labels: a port's display is a lang key with its text as fallback, and undisplayed ports use kg.pin.<id> keys with the id in words as fallback