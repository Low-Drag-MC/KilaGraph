## v26.1.0.14
* Fixed whole numbers exact in math, compare, bitwise and convert nodes
* Fixed collection ports the canonical LIST handle instead of the bare class
* Improved one prepared graph be executed by several threads
* Refactored Vector nodes of blueprint
* Added tangent space support to the shader graph
* Added the missing node wiki entries for ports and options
* Fixed a variable with a null default crashing
* Added the exec pin a node was entered through, and the Do Once, Do N, Flip Flop, Multi Gate and Toggle Gate nodes
* Added retainExecOutputs for exec node output cache
* Added object transform API slots
* Fixed DynamicTransforms being declared on pipelines that never bind it