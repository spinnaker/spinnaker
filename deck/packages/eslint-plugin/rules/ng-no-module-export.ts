/**
 * Prefer exporting a module's NAME instead of the entire angular.module()
 */

import type { AST, Rule } from 'eslint';
import type { AssignmentExpression, CallExpression, MemberExpression } from 'estree';
import { isCallExpression, isIdentifier, isMemberExpression } from '../utils/utils';

const rule = function (context: Rule.RuleContext) {
  function getSuggestedVariableNameForFile() {
    const filename = context.getFilename();
    if (filename.includes('/packages/')) {
      return filename
        .replace(/^.*\/packages\//g, '')
        .replace(/\/src\//g, '/')
        .replace(/\.[\w]*$/g, '')
        .replace(/[^\w_]/g, '_')
        .toUpperCase();
    }
  }

  return {
    AssignmentExpression: function (node: AssignmentExpression) {
      const left = node.left;
      const right = node.right;
      const isModuleExports = isModuleExportMemberExpression(left);
      const moduleNameNode = getAngularModuleNameNode(right);
      if (isModuleExports && moduleNameNode) {
        const message = 'Prefer exporting the AngularJS module name instead of the entire module';
        const variableName = getSuggestedVariableNameForFile();

        const fix = (fixer: Rule.RuleFixer) => {
          const assignmentRange = [left?.range[0], right?.range[0]] as AST.Range;
          const exportModuleVariable = `export const ${variableName} = ${moduleNameNode.raw};\n`;
          const exportNameVariable = `export const name = ${variableName}; // for backwards compatibility\n`;
          return [
            // Insert 'export const FOO = 'foo';
            fixer.insertTextBefore(node, exportModuleVariable + exportNameVariable),
            // Remove 'module.exports = '
            fixer.replaceTextRange(assignmentRange, ''),
            // Replace 'angular.module("foo"' with 'angular.module(FOO'
            fixer.replaceText(moduleNameNode, variableName),
          ];
        };

        if (variableName) {
          context.report({ node, message, fix });
        } else {
          context.report({ node, message });
        }
      }
    },
  };
};

function isModuleExportMemberExpression(node) {
  const object = node.object;
  const property = node.property;
  const isModuleExports = node.type === 'MemberExpression' && object.name === 'module' && property.name === 'exports';
  const isBareExports = node.type === 'Identifier' && node.name === 'exports';
  return isModuleExports || isBareExports;
}

function getAngularModuleNameNode(node) {
  if (!isCallExpression(node)) return false;
  const callee = node.callee as Rule.Node;

  function angularModuleNameNode(callExpression: CallExpression) {
    const isLiteral =
      callExpression.arguments && callExpression.arguments[0] && callExpression.arguments[0].type === 'Literal';
    return isLiteral ? callExpression.arguments[0] : undefined;
  }

  function isChainedCallExpression(_callee: Rule.Node): _callee is MemberExpression & Rule.NodeParentExtension {
    if (isMemberExpression(_callee)) {
      return _callee.object && _callee.object.type === 'CallExpression';
    }
  }

  function isAngularModuleCall(_callee) {
    if (isMemberExpression(_callee)) {
      return (
        _callee.object &&
        _callee.object.type === 'Identifier' &&
        _callee.object.name === 'angular' &&
        _callee.property &&
        'name' in _callee.property &&
        _callee.property.name === 'module'
      );
    }
  }

  function isRawModuleCall(_callee) {
    return isIdentifier(_callee) && _callee.name === 'module';
  }

  if (isChainedCallExpression(callee)) {
    return getAngularModuleNameNode(callee.object);
  } else if (isRawModuleCall(callee)) {
    if (node.arguments && node.arguments[0] && node.arguments[0].type === 'Literal') return angularModuleNameNode(node);
  } else if (isAngularModuleCall(callee)) {
    return angularModuleNameNode(node);
  }
}

const ruleModule: Rule.RuleModule = {
  meta: {
    type: 'problem',
    docs: {
      description: 'Instead of exporting the angular.module(), export the modules string identifier',
    },
    fixable: 'code',
  },
  create: rule,
};

export default ruleModule;
