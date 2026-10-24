## v26.1.0.16.a
* Fixed material uniforms declared after a vec3 reading the wrong value (they are now written at their std140 offsets)
* Fixed int and bool material uniforms being written as float bits
