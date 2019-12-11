'use strict';
const path = require('path');
const fs = require('fs');

/**
 * Prefer exporting a module's NAME instead of the entire angular.module()
 *
 * @version 0.1.0
 * @category conventions
 * @sinceAngularVersion 1.x
 */
const rule = function(context) {
  return {
    ArrayExpression: function(node) {
      if (isInAngularModuleCall(node)) {
        const requireDotNames = node.elements.map(element => getRequireDotNameNode(element)).filter(x => !!x);
        requireDotNames.forEach(([node, relativePath]) => {
          const message = `Prefer 'import { ANGULARJS_MODULE } from "./module"' over 'require("./module").name'`;
          const fix = getFixForRequireDotName(node, context.getFilename(), relativePath);
          context.report({ node, message, fix });
        });

        const requireDotAnythings = node.elements.map(element => getRequireDotAnythingNode(element)).filter(x => !!x);
        requireDotAnythings.forEach(([node, requiredString, propertyName]) => {
          const message = `Prefer 'import { default as ANGULARJS_MODULE } from "./module"' over 'require("./module").default'`;
          const fix = getFixForRequireDotAnything(node, requiredString, propertyName);
          context.report({ node, message, fix });
        });

        const bareRequires = node.elements.map(element => getBareRequireNode(element)).filter(x => !!x);
        bareRequires.forEach(([node, requiredString]) => {
          const message = `Prefer 'import ANGULARJS_LIBRARY from "angularjs-library"' over 'require("angularjs-library")'`;
          const fix = getFixForBareRequire(node, requiredString);
          context.report({ node, message, fix });
        });
      }
    },
  };
};

/*
Given:
angular.module('module', [
  require('angular-ui-bootstrap')
]);

Rewrites to:
import ANGULAR_UI_BOOTSTRAP from 'angular-ui-bootstrap';

angular.module('module', [
  ANGULAR_UI_BOOTSTRAP
]);
 */
function getFixForBareRequire(node, requiredString) {
  return function(fixer) {
    const variableName = requiredString.replace(/[^\w_]/g, '_').toUpperCase();
    const lastImport = findLastImportStatement(node);
    const importStatement = `\nimport ${variableName} from '${requiredString}';`;
    if (lastImport) {
      return [fixer.replaceText(node, variableName), fixer.insertTextAfter(lastImport, importStatement)];
    } else {
      return [fixer.replaceText(node, variableName), fixer.insertTextBeforeRange([0, 0], importStatement)];
    }
  };
}

/*
Given:
angular.module('module', [
  require('some/require/string').default
]);

Rewrites to:
import { default as SOME_REQUIRE_STRING } from 'some/require/string';

angular.module('module', [
  SOME_REQUIRE_STRING
]);
 */
function getFixForRequireDotAnything(node, requiredString, property) {
  return function(fixer) {
    const variableName = requiredString
      .replace(/^[^\w_]*/g, '')
      .replace(/[^\w_]/g, '_')
      .toUpperCase();
    const lastImport = findLastImportStatement(node);
    const importStatement = `\nimport { ${property} as ${variableName} } from '${requiredString}';`;
    if (lastImport) {
      return [fixer.replaceText(node, variableName), fixer.insertTextAfter(lastImport, importStatement)];
    } else {
      return [fixer.replaceText(node, variableName), fixer.insertTextBeforeRange([0, 0], importStatement)];
    }
  };
}

/*
Given:
angular.module('module', [
  require('./path/to/dependency').name
]);

Rewrites to:
import { DEPENDENCY_SYMBOL } from './path/to/dependency';

angular.module('module', [
  DEPENDENCY_SYMBOL
]);
 */
function getFixForRequireDotName(node, filename, relativePath) {
  const modulesPath = filename.replace(/modules\/.*/, 'modules/');

  const path1 = path.resolve(filename, '..', relativePath);
  const path2 = path.resolve(modulesPath, relativePath.replace(/^([a-zA-Z]+)\//, '$1/src/'));
  const path3 = path.resolve(modulesPath, relativePath);

  for (let path of [path1, path2, path3]) {
    for (let extension of ['.ts', '.js']) {
      if (fs.existsSync(path + extension)) {
        const fileSource = fs.readFileSync(path + extension).toString();
        const match = /export const name = ([\w_]*);/.exec(fileSource);
        if (match) {
          const variableName = match[1];
          return function(fixer) {
            const lastImport = findLastImportStatement(node);
            const importStatement = `\nimport { ${variableName} } from '${relativePath}';`;
            if (lastImport) {
              return [fixer.replaceText(node, variableName), fixer.insertTextAfter(lastImport, importStatement)];
            } else {
              return [fixer.replaceText(node, variableName), fixer.insertTextBeforeRange([0, 0], importStatement)];
            }
          };
        }
      }
    }
  }
}

function findLastImportStatement(_node) {
  let program = _node;
  while (program && program.parent) {
    program = program.parent;
  }

  if (program && program.type === 'Program') {
    const imports = program.body.filter(node => node.type === 'ImportDeclaration');
    if (imports.length) {
      return imports[imports.length - 1];
    }
  }
}

// require('./some/nested/angularjs/module').name
function getRequireDotNameNode(node) {
  if (node.type !== 'MemberExpression') return false;
  if (node.property.type !== 'Identifier' || node.property.name !== 'name') return false;
  if (node.object.type !== 'CallExpression' || node.object.callee.name !== 'require') return false;
  if (node.object.arguments.length !== 1 || node.object.arguments[0].type !== 'Literal') return false;

  // [node, './some/nested/angularjs/module']
  return [node, node.object.arguments[0].value];
}

// require('something').anything
function getRequireDotAnythingNode(node) {
  if (node.type !== 'MemberExpression') return false;
  if (node.property.type !== 'Identifier') return false;
  if (node.object.type !== 'CallExpression' || node.object.callee.name !== 'require') return false;
  if (node.object.arguments.length !== 1 || node.object.arguments[0].type !== 'Literal') return false;

  // [node, 'something', 'anything']
  return [node, node.object.arguments[0].value, node.property.name];
}

// require('something')
function getBareRequireNode(node) {
  if (node.type !== 'CallExpression' || node.callee.name !== 'require') return false;
  if (node.arguments.length !== 1 || node.arguments[0].type !== 'Literal') return false;

  // [node, 'something']
  return [node, node.arguments[0].value];
}

function isInAngularModuleCall(arrayExpression) {
  const { parent = {} } = arrayExpression;
  if (parent.type === 'CallExpression') {
    const { callee = {} } = parent;
    const { type, object, property } = callee;
    const isAngularModule =
      type === 'MemberExpression' && object && object.name === 'angular' && property && property.name === 'module';
    const isRawModule = type === 'Identifier' && callee.name === 'module';

    return isAngularModule || isRawModule;
  }
  return false;
}

module.exports = {
  meta: {
    type: 'problem',
    docs: {
      description: 'Import an angular module name symbol instead of using require("../foo").name',
    },
    fixable: 'code',
  },
  create: rule,
};
