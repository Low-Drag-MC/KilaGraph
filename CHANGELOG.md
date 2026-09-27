## v26.3.0.15
* hello 26.3!
* Added improved transparency (order-independent transparency) support to render type graphs; a blend it can't express (multiply, subtract, min, max, invert, glint) draws in the solid phase instead
* Added prepareInstanced, preparing an instanced draw before its render pass for executing in one, and a drawInstanced that draws into a given target
* Improved generated pipelines, now freed once their last material is closed
* Removed the render type Output Target setting: 26.3 draws a render type in the pass it is drawn in
* Extra colour targets are written by a draw into a pass that has them (drawInstanced, colorTargetPass); a draw in Minecraft's own passes writes the main target
* The Iris integration is off until Iris is released for NeoForge 26.3
