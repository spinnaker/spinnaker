/**
 * Prefer:
 * import { module } from 'angular';
 * module('mymodule', [])
 *
 * over:
 * import * as angular from 'angular';
 * angular.module('mymodule', [])
 */
import type { Rule, Scope } from 'eslint';
import type { ImportDeclaration, MemberExpression } from 'estree';
import { getProgram, isMemberExpression } from '../utils/utils';

const rule = function (context: Rule.RuleContext) {
  return {
    'MemberExpression[object.name="angular"][property.name="module"]': function (
      node: MemberExpression & Rule.NodeParentExtension,
    ) {
      const angularVar = findAngularVariable(node, context);
      const angularImport = findAngularImportStatement(node);
      // Double check that there is only a single use of 'angular' variable and that it's 'angular.module()')
      if (angularImport && angularVar && angularVar.references.length === 1) {
        const { parent } = angularVar.references[0].identifier as Rule.Node;
        if (isMemberExpression(parent)) {
          if (
            'name' in parent.object &&
            parent.object.name === 'angular' &&
            'name' in parent.property &&
            parent.property.name === 'module'
          ) {
            const message = "Prefer module('foo', []) to angular.module('foo', [])";
            const fix = getFixForAngularModule(node, angularImport);
            return context.report({ node, message, fix });
          }
        }
      }
    },
  };
};

function findAngularVariable(_node, context): Scope.Variable {
  const program = getProgram(_node);

  const programScope = context.getSourceCode().scopeManager.acquire(program);
  const moduleScope = programScope && programScope.childScopes.find((s) => s.type === 'module');
  return moduleScope && moduleScope.variables.find((v) => v.name === 'angular');
}

function findAngularImportStatement(_node): ImportDeclaration {
  let program = _node;
  while (program && program.parent) {
    program = program.parent;
  }

  return program.body.find((node) => {
    return (
      node.type === 'ImportDeclaration' &&
      node.source &&
      node.source.type === 'Literal' &&
      node.source.value === 'angular'
    );
  }) as ImportDeclaration;
}

/*
Given:
angular.module('module', ['dep']);

Rewrites to:
import { module } from 'angular';

module('module', ['dep']);
 */
function getFixForAngularModule(angularDotModuleNode: Rule.Node, importStatement: ImportDeclaration) {
  return function (fixer: Rule.RuleFixer) {
    return [
      fixer.replaceText(angularDotModuleNode, 'module'),
      fixer.replaceText(importStatement, `import { module } from 'angular';`),
    ];
  };
}

const ruleModule: Rule.RuleModule = {
  meta: {
    type: 'problem',
    docs: {
      description: `Prefer import { module } from 'angular' over angular.module()`,
    },
    fixable: 'code',
  },
  create: rule,
};

export default ruleModule;
