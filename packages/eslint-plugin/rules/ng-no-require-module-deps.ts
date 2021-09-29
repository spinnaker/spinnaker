import type { Rule } from 'eslint';
import type { ArrayExpression, Node } from 'estree';
import fs from 'fs';
import path from 'path';

/**
 * Prefer exporting a module's NAME instead of the entire angular.module()
 *
 * @version 0.1.0
 * @category conventions
 * @sinceAngularVersion 1.x
 */
const rule = function (context: Rule.RuleContext) {
  return {
    ArrayExpression: function (node: ArrayExpression) {
      if (isInAngularModuleCall(node)) {
        const requireDotNames = node.elements.map((element) => getRequireDotNameNode(element)).filter((x) => !!x);
        requireDotNames.forEach(([_node, relativePath]) => {
          const message = `Prefer 'import { ANGULARJS_MODULE } from "./module"' over 'require("./module").name'`;
          const fix = getFixForRequireDotName(_node, context.getFilename(), relativePath);
          context.report({ node: _node, message, fix });
        });

        const requireDotAnythings = node.elements
          .map((element) => getRequireDotAnythingNode(element))
          .filter((x) => !!x);
        requireDotAnythings.forEach(([_node, requiredString, propertyName]) => {
          const message = `Prefer 'import { default as ANGULARJS_MODULE } from "./module"' over 'require("./module").default'`;
          const fix = getFixForRequireDotAnything(_node, requiredString, propertyName);
          context.report({ node: _node, message, fix });
        });

        const bareRequires = node.elements.map((element) => getBareRequireNode(element)).filter((x) => !!x);
        bareRequires.forEach(([_node, requiredString]) => {
          const message = `Prefer 'import ANGULARJS_LIBRARY from "angularjs-library"' over 'require("angularjs-library")'`;
          const fix = getFixForBareRequire(_node, requiredString);
          context.report({ node: _node, message, fix });
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
function getFixForBareRequire(node: Node, requiredString: string) {
  return function (fixer: Rule.RuleFixer) {
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
function getFixForRequireDotAnything(node: Node, requiredString: string, property: string) {
  return function (fixer: Rule.RuleFixer) {
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
function getFixForRequireDotName(node: Node, filename: string, relativePath: string) {
  const modulesPath = filename.replace(/modules\/.*/, 'modules/');

  const path1 = path.resolve(filename, '..', relativePath);
  const path2 = path.resolve(modulesPath, relativePath.replace(/^([a-zA-Z]+)\//, '$1/src/'));
  const path3 = path.resolve(modulesPath, relativePath);

  for (const path of [path1, path2, path3]) {
    for (const extension of ['.ts', '.js']) {
      if (fs.existsSync(path + extension)) {
        const fileSource = fs.readFileSync(path + extension).toString();
        const match = /export const name = ([\w_]*);/.exec(fileSource);
        if (match) {
          const variableName = match[1];
          return function (fixer) {
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

function findLastImportStatement(_node: Node) {
  let program = _node as Rule.Node;
  while (program && program.parent) {
    program = program.parent;
  }

  if (program && program.type === 'Program') {
    const imports = program.body.filter((node) => node.type === 'ImportDeclaration');
    if (imports.length) {
      return imports[imports.length - 1];
    }
  }
}

// require('./some/nested/angularjs/module').name
function getRequireDotNameNode(node: Node): [Node, string] {
  if (node.type !== 'MemberExpression') return undefined;
  if (node.property.type !== 'Identifier' || node.property.name !== 'name') return undefined;
  if (node.object.type !== 'CallExpression' || (node.object.callee as any).name !== 'require') return undefined;
  if (node.object.arguments.length !== 1 || node.object.arguments[0].type !== 'Literal') return undefined;

  // [node, './some/nested/angularjs/module']
  return [node, node.object.arguments[0].value as string];
}

// require('something').anything
function getRequireDotAnythingNode(node: Node): [Node, string, string] {
  if (node.type !== 'MemberExpression') return undefined;
  if (node.property.type !== 'Identifier') return undefined;
  if (node.object.type !== 'CallExpression' || (node.object.callee as any).name !== 'require') return undefined;
  if (node.object.arguments.length !== 1 || node.object.arguments[0].type !== 'Literal') return undefined;

  // [node, 'something', 'anything']
  return [node, node.object.arguments[0].value as string, node.property.name];
}

// require('something')
function getBareRequireNode(node): [Node, string] {
  if (node.type !== 'CallExpression' || node.callee.name !== 'require') return undefined;
  if (node.arguments.length !== 1 || node.arguments[0].type !== 'Literal') return undefined;

  // [node, 'something']
  return [node, node.arguments[0].value as string];
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

const ruleModule: Rule.RuleModule = {
  meta: {
    type: 'problem',
    docs: {
      description: 'Import an angular module name symbol instead of using require("../foo").name',
    },
    fixable: 'code',
  },
  create: rule,
};

export default ruleModule;
