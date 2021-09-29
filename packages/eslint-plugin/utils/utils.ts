import type { Rule, Scope } from 'eslint';
import type {
  CallExpression,
  Expression,
  Identifier,
  Literal,
  MemberExpression,
  NewExpression,
  Node,
  Program,
  SpreadElement,
} from 'estree';
import * as _ from 'lodash/fp';

export const getNodeType = (obj: Node) => obj?.type;
export const isType = <T extends Node>(type: string) => (obj: Node): obj is T => getNodeType(obj) === type;
export const isIdentifier = isType<Identifier>('Identifier');
export const isCallExpression = isType<CallExpression>('CallExpression');
export const isMemberExpression = isType<MemberExpression>('MemberExpression');
export const isLiteral = isType<Literal>('Literal');
export const isNewExpression = isType<NewExpression>('NewExpression');

/**
 * Recursively grab the callee until an Identifier is found.
 *
 * API.all().all().one('foo/bar');
 *
 * var calleeOne = ...
 * getCallingIdentifier(calleeOne).name === 'API'
 */
export function getCallingIdentifier(calleeObject: Node): Identifier {
  if (isIdentifier(calleeObject)) {
    return calleeObject;
  }

  if (isCallExpression(calleeObject)) {
    const target = isMemberExpression(calleeObject.callee) ? calleeObject.callee.object : calleeObject.callee;
    return getCallingIdentifier(target);
  }

  return undefined;
}

export function getCallingIdentifierName(calleeObject: Node) {
  return getCallingIdentifier(calleeObject)?.name;
}

/**
 * given an identifier, finds its Variable in the enclosing scope
 */
export function getVariableInScope(context: Rule.RuleContext, identifier: Identifier): Scope.Variable {
  if (!isIdentifier(identifier)) {
    return undefined;
  }

  const { references } = context.getScope();
  const ref = references.find((r) => r.identifier.name === identifier.name);
  return ref ? ref.resolved : undefined;
}

export const getVariableInitializer = _.get('defs[0].node.init');

export function getProgram(node: Node): Program {
  let _node = node as Node & Rule.NodeParentExtension;
  while (_node.parent) {
    if (_node.parent.type === 'Program') {
      return _node.parent;
    }
    _node = _node.parent;
  }
  return undefined;
}

/**
 * Given a CallExpression: API.one().two().three().get();
 * Returns an array of the chained CallExpressions: [.one(), .two(), .three(), .get()]
 */
export const getCallChain = (node: Node): CallExpression[] => {
  if (isCallExpression(node) && isMemberExpression(node.callee) && isCallExpression(node.callee.object)) {
    return getCallChain(node.callee.object).concat(node);
  } else if (isCallExpression(node)) {
    return [node];
  }
  return [];
};

export function getArgsText(context: Rule.RuleContext, args: Array<Expression | SpreadElement>) {
  const sourceCode = context.getSourceCode();
  return (args || []).map((arg) => sourceCode.getText(arg)).join(', ');
}
