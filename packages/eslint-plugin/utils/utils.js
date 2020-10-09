/**
 * Recursively grab the callee until an Identifier is found.
 *
 * API.all().all().one('foo/bar');
 *
 * var calleeOne = ...
 * getCallingIdentifier(calleeOne).name === 'API'
 */
function getCallingIdentifier(calleeObject) {
  if (calleeObject.type && calleeObject.type === 'Identifier') {
    return calleeObject;
  } else if (calleeObject.callee && calleeObject.callee.object) {
    return getCallingIdentifier(calleeObject.callee.object);
  }
  return null;
}

/** given an identifier, finds its Variable in the enclosing scope */
function getVariableInScope(context, identifier) {
  const { variables } = context.getScope();
  return variables.find((v) => v.name === identifier.name);
}

module.exports = {
  getCallingIdentifier,
  getVariableInScope,
};
