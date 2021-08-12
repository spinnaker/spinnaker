'use strict';

/**
 * Prefer exporting a module's NAME instead of the entire angular.module()
 *
 * @version 0.1.0
 * @category conventions
 * @sinceAngularVersion 1.x
 */
const rule = function (context) {
  function getSuggestedVariableNameForFile() {
    const filename = context.getFilename();
    if (filename.includes('/modules/')) {
      return filename
        .replace(/^.*\/modules\//g, '')
        .replace(/\/src\//g, '/')
        .replace(/\.[\w]*$/g, '')
        .replace(/[^\w_]/g, '_')
        .toUpperCase();
    }
  }

  return {
    AssignmentExpression: function (node) {
      const { left = {}, right = {} } = node;
      const isModuleExports = isModuleExportMemberExpression(left);
      const moduleNameNode = getAngularModuleNameNode(right);
      if (isModuleExports && moduleNameNode) {
        const message = 'Prefer exporting the AngularJS module name instead of the entire module';
        const variableName = getSuggestedVariableNameForFile();

        const fix = (fixer) => {
          const assignmentRange = [left.range[0], right.range[0]];
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
  const { object = {}, property = {} } = node;
  const isModuleExports = node.type === 'MemberExpression' && object.name === 'module' && property.name === 'exports';
  const isBareExports = node.type === 'Identifier' && node.name === 'exports';
  return isModuleExports || isBareExports;
}

function getAngularModuleNameNode(node) {
  const { callee } = node;
  if (node.type !== 'CallExpression') return false;

  function angularModuleNameNode(callExpression) {
    const isLiteral =
      callExpression.arguments && callExpression.arguments[0] && callExpression.arguments[0].type === 'Literal';
    return isLiteral ? callExpression.arguments[0] : undefined;
  }

  function isChainedCallExpression(_callee) {
    return _callee.type === 'MemberExpression' && callee.object && callee.object.type === 'CallExpression';
  }

  function isAngularModuleCall(_callee) {
    return (
      _callee.type === 'MemberExpression' &&
      callee.object &&
      callee.object.type === 'Identifier' &&
      callee.object.name === 'angular' &&
      callee.property &&
      callee.property.name === 'module'
    );
  }

  function isRawModuleCall(_callee) {
    return _callee.type === 'Identifier' && callee.name === 'module';
  }

  if (isChainedCallExpression(callee)) {
    return getAngularModuleNameNode(callee.object, node);
  } else if (isRawModuleCall(callee)) {
    if (node.arguments && node.arguments[0] && node.arguments[0].type === 'Literal') return angularModuleNameNode(node);
  } else if (isAngularModuleCall(callee)) {
    return angularModuleNameNode(node);
  }
}

module.exports = {
  meta: {
    type: 'problem',
    docs: {
      description: 'Instead of exporting the angular.module(), export the modules string identifier',
    },
    fixable: 'code',
  },
  create: rule,
};
