## v26.3.0.15
* hello 26.3!
* Added improved transparency (order-independent transparency) support to render type graphs; a blend it can't express (multiply, subtract, min, max, invert, glint) draws in the solid phase with either setting, as vanilla's glint does
* Added prepareInstanced, preparing an instanced draw before its render pass for executing in one, and a drawInstanced that draws into a given target
* Improved a draw's values: it binds the values, textures, instances and transforms its render type was prepared with, like Minecraft's own per-draw transforms
* Improved generated pipelines, now freed once their last material is closed
* Improved the editor's previews, whose shaders now compile in the background instead of stalling the editor on every edit
* Fixed a closed material's render type failing the frame when still submitted; its draws are skipped
* Removed the render type Output Target setting: 26.3 draws a render type in the pass it is drawn in
* Extra colour targets are written by a draw into a pass that has them (drawInstanced, colorTargetPass); a draw in Minecraft's own passes writes the main target
* The Iris integration is off until Iris is released for NeoForge 26.3
