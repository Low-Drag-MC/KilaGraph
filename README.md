# KilaGraph

<div align="center">

**A programmable node graph and shader graph toolkit for Minecraft mod developers.**

[![NeoForge](https://img.shields.io/badge/NeoForge-26.1+-E04E14?style=for-the-badge)](https://neoforged.net/)
[![LDLib2](https://img.shields.io/badge/Built%20on-LDLib2-2F80ED?style=for-the-badge)](https://github.com/Low-Drag-MC/LDLib2)
[![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)](LICENSE)

</div>

KilaGraph builds on LDLib2's Node Graph Toolkit to provide in-game programmable graphs for mods. It is aimed at authors who want visual logic graphs, reusable graph resources, and ShaderGraph-style authoring for custom RenderTypes without writing every shader and editor workflow by hand.

## Features

- **Blueprint Graphs**: general-purpose programmable node graphs for mod-side logic and data flow.
- **RenderType Graphs**: visual shader graphs that compile into Minecraft RenderType pipelines.
- **Shader Function Graphs**: reusable shader subgraphs that can be shared across RenderType graphs.
- **Editor integration**: LDLib2-powered graph editing, resource management, node libraries, settings panels, and live shader previews.
- **Minecraft-aware nodes**: utilities for items, blocks, fluids, entities, NBT, world queries, math, lists, maps, strings, and shader operations.

## Requirements

- Minecraft / NeoForge `26.1`
- LDLib2 `26.1.2.20+`

Exact dependency ranges are defined in `gradle.properties`.

## Links

- [LDLib2](https://github.com/Low-Drag-MC/LDLib2)
- [KilaGraph repository](https://github.com/Low-Drag-MC/KilaGraph)

## License

KilaGraph is licensed under the MIT license.
