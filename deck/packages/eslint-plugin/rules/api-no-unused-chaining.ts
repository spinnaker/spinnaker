import type { Rule } from 'eslint';
import * as _ from 'lodash/fp';

const isApiConfigCall = _.overSome([
  _.matches({ property: { type: 'Identifier', name: 'one' } }),
  _.matches({ property: { type: 'Identifier', name: 'all' } }),
  _.matches({ property: { type: 'Identifier', name: 'useCache' } }),
  _.matches({ property: { type: 'Identifier', name: 'withParams' } }),
  _.matches({ property: { type: 'Identifier', name: 'data' } }),
]);

const falsePostitives = _.overSome([
  _.matches({
    property: { type: 'Identifier', name: 'data' },
    object: { type: 'Identifier', name: '$element' },
  }),
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

const ruleModule: Rule.RuleModule = {
  create,
  meta: {
    type: 'problem',
    docs: {
      description: 'Check for unused API.xyx() calls',
    },
  },
};

export default ruleModule;
