import type { Rule, Scope } from 'eslint';
import camelCase from 'lodash/camelCase';

const findParentNodeByType = (node: Rule.Node, type: string) =>
  !node ? null : node.type === type ? node : findParentNodeByType(node.parent, type);

/**
 * Use object literal when declaring AngularJS components
 * Do not use new ComponentClass()
 *
 * @version 0.1.0
 * @category conventions
 */
import angularRule from '../utils/angular-rule/angular-rule';
import { isNewExpression } from '../utils/utils';

const useObjectLiteral = function (context: Rule.RuleContext) {
  return {
    'angular?component': function (callee, thisGuy) {
      const node: Rule.Node = thisGuy.node;
      const scope: Scope.Scope = thisGuy.scope;
      if (isNewExpression(node)) {
        const calleeName = 'name' in node.callee ? node.callee.name : undefined;
        const fix = (fixer: Rule.RuleFixer) => {
          const variable = scope.variables.find((x) => x.name === calleeName);
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

const ruleModule: Rule.RuleModule = {
  meta: {
    type: 'problem',
    fixable: 'code',
    docs: {
      description: 'Prefer .component("foo", {}) over .component("foo", new FooComponentClass())',
    },
  },
  create: angularRule(useObjectLiteral),
};
export default ruleModule;
