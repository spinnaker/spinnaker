'use strict';
const { getCallingIdentifier, getVariableInScope } = require('../utils/utils');
const _ = require('lodash');

/**
 * No slashes in string literals passed to API.one() / API.all()
 *
 * @version 0.1.0
 * @category
 */
const rule = function (context) {
  return {
    CallExpression: function (node) {
      const { callee = {}, arguments: args = [] } = node;

      // .one() or .all()
      const propertyName = (callee.property && callee.property.name) || '';
      if (propertyName !== 'one' && propertyName !== 'all') {
        // console.log('not one or all');
        return;
      }

      // API.one('foo/bad')
      //          ^^^^^^^
      // API.all('ok').one('ok', 'foo/bad', 'ok')
      //                          ^^^^^^^
      const literalArgs = args.filter((arg) => arg.type === 'Literal' && arg.raw.includes('/'));

      // var badvar = 'foo/bad'; API.one(badvar);
      //                                 ^^^^^^
      const variableArgs = args.filter((argument) => {
        if (argument.type !== 'Identifier') {
          return false;
        }
        const variable = getVariableInScope(context, argument);
        const literalValue = _.get(variable, 'defs[0].node.init.raw', '');
        return literalValue.includes('/');
      });

      if (literalArgs.length === 0 && variableArgs.length === 0) {
        // console.log('no slashes');
        return;
      }

      // API.all('ok').one('ok', 'foo/bad', 'ok')
      // ^^^
      if ((getCallingIdentifier(node) || {}).name !== 'API') {
        // console.log(getCallingIdentifier(callee));
        // console.log('calling identifier not API');
        return;
      }

      const message =
        `Do not include slashes in API.one() or API.all() calls because arguments to .one() and .all() get url encoded.` +
        `Instead, of API.one('foo/bar'), split into multiple arguments: API.one('foo', 'bar').`;

      const fix = (fixer) => {
        // within:
        //   API.one('foo/bad')
        // replaces:
        //   'foo/bad'
        // with:
        //   'foo', 'bad'
        const literalArgFixes = literalArgs.map((arg) => {
          const varArgs = arg.value
            .split('/')
            .map((segment) => "'" + segment + "'")
            .join(', ');

          return fixer.replaceText(arg, varArgs);
        });

        // within:
        //   let varDeclaration = 'foo/bad';
        //   API.one(varDeclaration)
        // replaces argument:
        //   varDeclaration
        // with:
        //   ...varDeclaration.split('/')
        const variableArgFixes = variableArgs.map((arg) => {
          const spread = `...${arg.name}.split('/')`;
          return fixer.replaceText(arg, spread);
        });

        return literalArgFixes.concat(variableArgFixes);
      };
      context.report({ fix, node, message });
    },
  };
};

module.exports = {
  meta: {
    type: 'problem',
    docs: {
      description: ``,
    },
    fixable: 'code',
  },
  create: rule,
};
