## v26.3.1.0
* Added Iris 1.11.7 support for NeoForge 26.3: a shaderpack draws render type graphs with its own entity programs, running their shading, textures, per-material values and vertex displacement there, and casting their shadows
* Improved shaderpack reloads, now about 4 times faster: graph surfaces are injected only into the entity programs they draw with
* Improved the shaderpack surface cache, keeping only the surfaces in use and the 32 most recently released in the pack's programs
* Fixed a shaderpack failing to load once its programs can't hold every graph's uniforms and samplers; a graph that doesn't fit is left out of them, with a warning
* A shaderpack draws graphs with the entity vertex format, one RGBA8 target and no instance data; others keep their own shader, unlit by the pack
* Iris runs on the OpenGL backend only, and turns improved transparency off while its shaders are enabled
