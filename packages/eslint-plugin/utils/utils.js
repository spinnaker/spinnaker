/**
 * @typedef {import('estree').Context} Context
 * @typedef {import('estree').Program} Program
 * @typedef {import('estree').Variable} Variable
 */

const _ = require('lodash/fp');

const getNodeType = (obj) => obj && obj.type;
const isIdentifier = (obj) => getNodeType(obj) === 'Identifier';

/**
 * Recursively grab the callee until an Identifier is found.
 *
 * API.all().all().one('foo/bar');
 *
 * var calleeOne = ...
 * getCallingIdentifier(calleeOne).name === 'API'
 * @returns {Identifier}
 */
function getCallingIdentifier(calleeObject) {
  if (!calleeObject) {
    return undefined;
  } else if (isIdentifier(calleeObject)) {
    return calleeObject;
  } else if (isIdentifier(calleeObject.callee)) {
    return calleeObject.callee;
  } else if (calleeObject.callee && calleeObject.callee.object) {
    return getCallingIdentifier(calleeObject.callee.object);
  }
}

function getCallingIdentifierName(calleeObject) {
  const identifier = getCallingIdentifier(calleeObject);
  return identifier ? identifier.name : undefined;
}

/**
 * given an identifier, finds its Variable in the enclosing scope
 * @param context {RuleContext}
 * @param identifier {Identifier}
 * @returns {Variable}
 */
function getVariableInScope(context, identifier) {
  if (identifier && identifier.type === 'Identifier') {
    const { references } = context.getScope();
    const ref = references.find((r) => r.identifier.name === identifier.name);
    return ref ? ref.resolved : undefined;
  }
}

const getVariableInitializer = _.get('defs[0].node.init');

/** @returns {Program} */
function getProgram(node) {
  while (node.type !== 'Program' && node.parent) {
    node = node.parent;
  }
  return node;
}

const isNestedCallExpr = _.matches({ callee: { type: 'MemberExpression', object: { type: 'CallExpression' } } });

/**
 * Given a CallExpression: API.one().two().three().get();
 * Returns an array of the chained CallExpressions: [.one(), .two(), .three(), .get()]
 * @param node {CallExpression}
 * @returns {CallExpression[]}
 */
const getCallChain = (node) => {
  if (isNestedCallExpr(node)) {
    return getCallChain(node.callee.object).concat(node);
  } else {
    return [node];
  }
};

/**
 * @param context {RuleContext}
 * @param args {Argument[]}
 */
function getArgsText(context, args) {
  const sourceCode = context.getSourceCode();
  return (args || []).map((arg) => sourceCode.getText(arg)).join(', ');
}

module.exports = {
  getArgsText,
  getCallingIdentifier,
  getCallingIdentifierName,
  getCallChain,
  getNodeType,
  getProgram,
  getVariableInitializer,
  getVariableInScope,
};
