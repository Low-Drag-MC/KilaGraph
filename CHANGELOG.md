## v21.1.0.17
* Added an Enum node, picking one value of an enum from a list
* Added a type option to Equals and Not Equals: pick a type and every input becomes that type, so an unwired input can be set to one of its values (such as an enum's)
* Added EvaluationEnvironment#onVariableSet, told after every SetVar so a host can notify where the write happens