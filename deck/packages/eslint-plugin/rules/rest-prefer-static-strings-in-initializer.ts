/**
 * Migrate REST().path('foo', 'bar').path('baz').get() to REST('/foo/bar/baz').get()
 */

import type { Rule } from 'eslint';
import type { CallExpression, Literal } from 'estree';
import * as _ from 'lodash/fp';

import { getCallChain, getCallingIdentifierName, isLiteral } from '../utils/utils';
const getCallName = _.get('callee.property.name');

const ruleModule: Rule.RuleModule = {
  create(context) {
    return {
      /**
       * Look for chains of CallExpressions that are part of a REST().path() call
       */
      CallExpression(node: CallExpression & Rule.NodeParentExtension) {
        const callingIdentifierName = getCallingIdentifierName(node);
        if (node.parent.type === 'MemberExpression' || callingIdentifierName !== 'REST') {
          return undefined;
        }

        // an array of CallExpressions, i.e. for API.one().all().get() -> [.one, .all, .get]
        const callChain = getCallChain(node);

        // Look for a REST().path().whatever() call
        if (!callChain[1] || getCallName(callChain[1]) !== 'path') {
          return;
        }

        const restCall = callChain[0];
        const pathCall = callChain[1];

        const restArg = restCall.arguments[0] as Literal;
        const firstPathArg = pathCall.arguments[0] as Literal;

        // Only REST('literal').path('literal', ...)
        // Ignores: REST(variable) and REST().path(variable)
        if ((restArg && !isLiteral(restArg)) || !isLiteral(firstPathArg)) {
          return undefined;
        }

        const message = `Prefer REST('/foo/bar') over REST().path('foo', 'bar')`;

        function fix(fixer) {
          const fixes = [];
          const restCallEnd = restCall.range[1];
          if (restArg) {
            // REST('/foo').path('bar')
            // Join '/foo' and '/bar' and replace the rest arg
            // REST('/foo/bar').path('bar');
            fixes.push(fixer.replaceText(restArg, `'${restArg.value}/${firstPathArg.value}'`));
          } else {
            // REST().path('foo')
            // Insert text between the parentheses
            // REST('foo').path('foo');
            fixes.push(fixer.insertTextAfterRange([restCallEnd - 1, restCallEnd - 1], `'/${firstPathArg.value}'`));
          }

          if (pathCall.arguments.length === 1) {
            // REST('foo').path('foo');
            // Remove the entire .path() call
            // REST('foo');
            fixes.push(fixer.removeRange([restCallEnd, pathCall.range[1]]));
          } else {
            /** @type {Literal} */
            const secondPathArg = pathCall.arguments[1];
            // Remove the first .path() call argument
            fixes.push(fixer.removeRange([firstPathArg.range[0], secondPathArg.range[0]]));
          }

          return fixes;
        }

        context.report({ node, message, fix });
      },
    };
  },
  meta: {
    fixable: 'code',
    type: 'problem',
    docs: {
      description: 'Migrate from API.xyz() to REST(path)',
    },
  },
};

export default ruleModule;
