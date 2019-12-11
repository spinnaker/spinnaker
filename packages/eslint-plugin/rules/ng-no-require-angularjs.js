'use strict';

/**
 * Prefer:
 * import { module } from 'angular';
 * module('mymodule', [])
 *
 * over:
 * import * as angular from 'angular';
 * angular.module('mymodule', [])
 *
 * @version 0.1.0
 * @category conventions
 * @sinceAngularVersion 1.x
 */
const rule = function(context) {
  return {
    MemberExpression: function(node) {
      const { type, object = {}, property = {} } = node;
      const isAngularDotModule = type === 'MemberExpression' && object.name === 'angular' && property.name === 'module';
      if (isAngularDotModule) {
        const angularVar = findAngularVariable(node, context);
        const angularImport = findAngularImportStatement(node);
        // Double check that there is only a single use of 'angular' variable and that it's 'angular.module()')
        if (angularImport && angularVar && angularVar.references.length === 1) {
          const { parent } = angularVar.references[0].identifier;
          if (
            parent.type === 'MemberExpression' &&
            parent.object.name === 'angular' &&
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

function findAngularVariable(_node, context) {
  let program = _node;
  while (program && program.parent) {
    program = program.parent;
  }

  const programScope = context.getSourceCode().scopeManager.acquire(program);
  const moduleScope = programScope && programScope.childScopes.find(s => s.type === 'module');
  return moduleScope && moduleScope.variables.find(v => v.name === 'angular');
}

function findAngularImportStatement(_node) {
  let program = _node;
  while (program && program.parent) {
    program = program.parent;
  }

  return program.body.find(node => {
    return (
      node.type === 'ImportDeclaration' &&
      node.source &&
      node.source.type === 'Literal' &&
      node.source.value === 'angular'
    );
  });
}

/*
Given:
angular.module('module', ['dep']);

Rewrites to:
import { module } from 'angular';

module('module', ['dep']);
 */
function getFixForAngularModule(angularDotModuleNode, importStatement) {
  return function(fixer) {
    return [
      fixer.replaceText(angularDotModuleNode, 'module'),
      fixer.replaceText(importStatement, `import { module } from 'angular'`),
    ];
  };
}

module.exports = {
  meta: {
    type: 'problem',
    docs: {
      description: `Prefer import { module } from 'angular' over angular.module()`,
    },
    fixable: 'code',
  },
  create: rule,
};
