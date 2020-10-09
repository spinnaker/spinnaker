'use strict';
const camelCase = require('lodash/camelCase');

const findParentNodeByType = (node, type) =>
  !node ? null : node.type === type ? node : findParentNodeByType(node.parent, type);
/**
 * Use object literal when declaring AngularJS components
 * Do not use new ComponentClass()
 *
 * @version 0.1.0
 * @category conventions
 */
const angularRule = require('../utils/angular-rule/angular-rule');
const useObjectLiteral = function (context) {
  return {
    'angular?component': function (callee, thisGuy) {
      const { node, scope } = thisGuy;
      if (node.type === 'NewExpression') {
        const fix = (fixer) => {
          const variable = scope.variables.find((x) => x.name === node.callee.name);
          const classDef = variable.defs[0].node;
          const name = classDef.id.name;
          const camelCaseName = camelCase(name);
          const rename = name !== camelCaseName;

          const objProperties = classDef.body.body.map((node) => {
            return node.key.name + ': ' + context.getSourceCode().getText(node.value);
          });

          const impls = (classDef.implements || []).map((impl) => context.getSourceCode().getText(impl));
          const identifier = `const ${camelCaseName}${impls.length ? `: ${impls.join(' & ')}` : ''}`;
          const objectLiteral = `${identifier} = {\n` + `  ${objProperties.join(',\n  ')}\n` + `};`;

          const otherReferences = findParentNodeByType(classDef, 'Program').tokens.filter((token) => {
            const ignores = [classDef.id.range[0], node.callee.range[0]];
            return (
              token.type === 'Identifier' && token.value === name && ignores.every((pos) => pos !== token.range[0])
            );
          });

          const otherFixes = rename ? [] : otherReferences.map((id) => fixer.replaceText(id, camelCaseName));

          return [fixer.replaceText(classDef, objectLiteral), fixer.replaceText(node, camelCaseName), ...otherFixes];
        };

        const message = 'Use .component("foo", {}) instead of .component("foo", new FooComponentClass())';
        context.report({ fix, node, message });
      }
    },
  };
};

module.exports = {
  meta: {
    type: 'problem',
    fixable: 'code',
    docs: {
      description: 'Prefer .component("foo", {}) over .component("foo", new FooComponentClass())',
    },
  },
  create: angularRule(useObjectLiteral),
};
