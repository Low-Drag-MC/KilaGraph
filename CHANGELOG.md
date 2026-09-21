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