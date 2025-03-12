/**
 * require a consistent DI syntax
 *
 * All your DI should use the same syntax : the Array, function, or $inject syntaxes ("di":  [2, "array, function, or $inject"])
 *
 * @version 0.1.0
 * @category conventions
 * @sinceAngularVersion 1.x
 */
import type { Rule } from 'eslint';
import { isEqual } from 'lodash';

import angularRule from '../utils/angular-rule/angular-rule';
import utils from '../utils/angular-rule/utils';

const stripUnderscores = true;

function normalizeParameter(param) {
  return stripUnderscores ? param : param.replace(/^_(.+)_$/, (match, p1) => p1);
}

const rule = function (context: Rule.RuleContext) {
  const $injectProperties = {};

  function maybeNoteInjection(node) {
    if (
      node.left &&
      node.left.property &&
      ((utils.isLiteralType(node.left.property) && node.left.property.value === '$inject') ||
        (utils.isIdentifierType(node.left.property) && node.left.property.name === '$inject'))
    ) {
      $injectProperties[node.left.object.name] = node.right;
    }
  }

  function getDiStrings(name: string) {
    const $inject = $injectProperties[name];
    const elements = $inject && ($inject.elements || $inject.expression.elements);
    return elements && elements.map((el) => el.value);
  }

  function compareParamsAndDI(node, name, type, context, params, diStrings) {
    const paramNames = params.map((p) => normalizeParameter(p));
    const diCount = diStrings ? diStrings.length : 0;
    const paramCount = paramNames.length;

    if (diCount === 0 && diCount !== paramCount) {
      const message =
        `The injected function${name ? ` '${name}'` : ''} ` +
        `has ${paramCount} parameter(s): ${JSON.stringify(paramNames)}, ` +
        `but no annotation was found`;

      const injectStrings = params.map((p) => `'${p}'`).join(', ');

      const fix = (fixer) => {
        if (name && type === 'tsclass') {
          return fixer.insertTextBefore(node, `public static $inject = [${injectStrings}];\n  `);
        } else if (name && type === 'class') {
          // find class node
          let classNode = node;
          while (classNode.type !== 'ClassDeclaration' && classNode.parent) {
            classNode = classNode.parent;
          }
          return fixer.insertTextAfter(classNode, `\n${name}.$inject = [${injectStrings}];`);
        } else if (name) {
          return fixer.insertTextAfter(node, `\n${name}.$inject = [${injectStrings}];`);
        } else {
          return [fixer.insertTextBefore(node, `[${injectStrings}, `), fixer.insertTextAfter(node, `]`)];
        }
      };

      // let program = node;
      // while(program.parent) {program = program.parent}
      // console.log(context.getSourceCode().getText(program));

      context.report({ node, message, fix });
    } else if (diCount !== paramCount) {
      const message =
        `The injected function${name ? ` '${name}'` : ''} ` +
        `has ${paramCount} parameter(s): ${JSON.stringify(paramNames)}, ` +
        `but there were ${diCount} DI strings${diCount === 0 ? '' : `: ${JSON.stringify(diStrings)} `}`;
      context.report({ node, message });
    } else if (!isEqual(diStrings, paramNames)) {
      const message =
        `The injected function${name ? ` '${name}'` : ''} ` +
        `parameter names: ${JSON.stringify(paramNames)} ` +
        `do not match the DI strings: ${JSON.stringify(diStrings)}`;
      context.report({ node, message });
    }
  }

  function fromArray(thisGuy) {
    const { node, scope, callExpression } = thisGuy;
    const args = node.elements.slice(0, -1);
    const fn = node.elements.slice(-1)[0];
    const diStrings = args.map((node) => node.value);

    if (fn.type === 'Identifier') {
      const name = fn.name;
      const result = fromIdentifier({ node: fn, scope, callExpression });
      const params = result.fn.params && result.fn.params.map((param) => param.name);
      return { type: 'array', fn: result.fn, name, params, diStrings };
    }

    const params = fn.params && fn.params.map((param) => param.name);
    return { type: 'array', fn, name: undefined, params, diStrings };
  }

  function fromIdentifier(thisGuy) {
    const { node, scope } = thisGuy;
    const reference = scope.references.find((r) => r.identifier.name === node.name);
    const resolved = reference && reference.resolved;
    if (resolved) {
      const { defs, scope: resolvedScope } = resolved;
      return processThisGuy({ node: defs[0].node, scope: resolvedScope });
    }
  }

  function fromVariableDeclarator(thisGuy) {
    const { node, scope } = thisGuy;
    const { name } = node.id;
    const fn = node.init;

    const variable = scope.variables.find((v) => v.name === name);

    if (!variable) {
      throw new Error(`Weird, I couldn't find variable '${name}' in scope?`);
    }

    if (variable.defs.length > 1) {
      throw new Error('It is pretty unexpected to find more than one def in this guys variable?');
    }

    const params = fn.params.map((param) => param.name);
    const diStrings = getDiStrings(name);

    // TODO: is this really function?
    return { type: 'function', fn, name, params, diStrings };
  }

  function fromClassDeclaration(thisGuy) {
    const { node } = thisGuy;
    const { name } = node.id;
    const ctor = node.body.body.find((node) => node.type === 'MethodDefinition' && node.kind === 'constructor');
    if (!ctor) return null;
    const isTypescript = !!context.getFilename().match(/\.tsx?$/);
    const params = ctor.value.params.map((param) =>
      param.type === 'TSParameterProperty' ? param.parameter.name : param.name,
    );
    const $inject = node.body.body.find(
      (node) => node.type === 'ClassProperty' && node.static && node.key.name === '$inject',
    );
    const diStrings = $inject ? $inject.value.elements.map((el) => el.value) : getDiStrings(name);
    return { type: isTypescript ? 'tsclass' : 'class', fn: ctor, name, params, diStrings };
  }

  function fromFunction(thisGuy) {
    const { node: fn } = thisGuy;
    const name = fn.type === 'FunctionDeclaration' ? fn.id.name : fn.name;
    const params = fn.params.map((param) => param.name);
    const diStrings = getDiStrings(name);
    return { type: 'function', fn, name, params, diStrings };
  }

  function processThisGuy(thisGuy) {
    if (!thisGuy || !thisGuy.node) {
      throw new Error('processThisGuy: Unexpected null argument');
    }

    switch (thisGuy.node.type) {
      case 'ArrayExpression':
        return fromArray(thisGuy);
      case 'ArrowFunctionExpression':
      case 'FunctionExpression':
      case 'FunctionDeclaration':
        return fromFunction(thisGuy);
      case 'Identifier':
        return fromIdentifier(thisGuy);
      case 'VariableDeclarator':
        return fromVariableDeclarator(thisGuy);
      case 'ClassDeclaration':
        return fromClassDeclaration(thisGuy);
      case 'MemberExpression': {
        const memberExpression = context.getSourceCode().getText(thisGuy.node);
        // allowlist some known symbols
        if (!['angular.noop', 'noop'].includes(memberExpression)) {
          console.warn(`Unable to handle MemberExpression: ${memberExpression}`);
        }
        return null;
      }
      case 'ImportSpecifier': {
        // const importSpecifier = context.getSourceCode().getText(thisGuy.node);
        // console.warn(`warn: Unable to handle ImportSpecifier: ${importSpecifier} in ${context.getFilename()}`);
        return null;
      }
      default:
        console.error(context.getSourceCode().getText(thisGuy.node));
        throw new Error(`Unknown type: ${thisGuy.node.type}`);
    }
  }

  function checkDi(callee, thisGuy) {
    if (!thisGuy) {
      throw new Error('checkDi: unexpected null argument');
    } else if (!thisGuy.node) {
      throw new Error('checkDi: missing node in thisGuy');
    }

    let result;
    try {
      result = processThisGuy(thisGuy);
    } catch (error) {
      console.error(`Internal error while processing ${context.getFilename()}`);
      console.error(context.getSourceCode().getText(thisGuy.callExpression));
      throw error;
    }
    if (!result) return;
    const { type, fn, name, params, diStrings } = result;

    // If there's an array, validate it
    if (type === 'array') {
      const expectedTypes = ['ArrowFunctionExpression', 'FunctionExpression', 'FunctionDeclaration'];
      if (!expectedTypes.includes(fn.type)) {
        const message = `Array-style: The last element should be an injected function, but it was: ${fn.type}`;
        return context.report({ node: fn, message });
      }

      if (!diStrings.every((str) => typeof str === 'string')) {
        return context.report({ node: fn, message: `Array-style: Elements [0..n-2] should all be strings` });
      }
    }

    if (params.length) {
      compareParamsAndDI(fn, name, type, context, params, diStrings);
    }
  }

  return {
    'angular?animation': checkDi,
    'angular?config': checkDi,
    'angular?controller': checkDi,
    'angular?component': function (callee, thisGuy) {
      if (thisGuy.node.type === 'ObjectExpression') {
        const property = thisGuy.node.properties.find((prop) => prop.key.name === 'controller');
        if (property) {
          if (property.value.type !== 'Literal') {
            return checkDi(callee, Object.assign({}, thisGuy, { node: property.value }));
          }
        }
      }
    },
    'angular?decorator': checkDi,
    'angular?directive': function (callee, thisGuy) {
      if (thisGuy.node.type === 'ObjectExpression') {
        const property = thisGuy.node.properties.find((prop) => prop.key.name === 'controller');
        if (property) {
          if (property.value.type !== 'Literal') {
            return checkDi(callee, Object.assign({}, thisGuy, { node: property.value }));
          }
        }
      }
    },
    'angular?factory': checkDi,
    'angular?filter': checkDi,
    'angular?inject': checkDi,
    'angular?run': checkDi,
    'angular?service': checkDi,
    'angular?provider': function (callee, providerFn, $get) {
      checkDi(null, providerFn);
      checkDi(null, $get);
    },
    'CallExpression:exit': function (node) {
      const { object, property } = node.callee;
      if (object && object.name === '$provide' && property && property.name === 'decorator') {
        checkDi(null, { node: node.arguments[1], scope: context.getScope() });
      }
    },
    AssignmentExpression: function (node) {
      maybeNoteInjection(node);
    },
    ClassDeclaration: function (node) {
      const interfaces = ['IController', 'ng.IController'];
      const implementsIController = (node.implements || []).some((impl) => interfaces.includes(impl.expression.name));
      const isNamedSortaLikeOne = node.id.name.match(/(Ctrl|Controller)$/);
      const isClassController = implementsIController || isNamedSortaLikeOne;

      if (isClassController) {
        checkDi(null, { node: node });
      }
    },
  };
};

const ruleModule: Rule.RuleModule = {
  meta: {
    type: 'problem',
    docs: {
      description: 'All angularjs functions must be explicitly annotated',
    },
    fixable: 'code',
  },
  create: angularRule(rule),
};
export default ruleModule;
