'use strict';

const _ = require('lodash/fp');

const isApiConfigCall = _.overSome([
  { property: { type: 'Identifier', name: 'one' } },
  { property: { type: 'Identifier', name: 'all' } },
  { property: { type: 'Identifier', name: 'useCache' } },
  { property: { type: 'Identifier', name: 'withParams' } },
  { property: { type: 'Identifier', name: 'data' } },
]);

const falsePostitives = _.overSome([
  { property: { type: 'Identifier', name: 'data' }, object: { type: 'Identifier', name: '$element' } },
]);

const create = function (context) {
  return {
    ExpressionStatement(node) {
      if (
        node.expression.type === 'CallExpression' &&
        isApiConfigCall(node.expression.callee) &&
        !falsePostitives(node.expression.callee)
      ) {
        const text = context.getSourceCode().getText(node);
        context.report({
          node,
          message: `Unused API.xyz() method chaining no longer works. Re-assign the result of: ${text}`,
        });
      }
    },
  };
};

module.exports = {
  create,
  meta: {
    docs: {
      description: 'Check for unused API.xyx() calls',
      recommended: 'error',
    },
  },
};
