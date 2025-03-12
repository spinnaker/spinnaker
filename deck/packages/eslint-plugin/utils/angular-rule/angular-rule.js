'use strict';

module.exports = angularRule;

/**
 * Method names from an AngularJS module which can be chained.
 */
var angularChainableNames = [
  'animation',
  'component',
  'config',
  'constant',
  'controller',
  'decorator',
  'directive',
  'factory',
  'filter',
  'provider',
  'run',
  'service',
  'value',
];

/**
 * An angularRule defines a simplified interface for AngularJS component based rules.
 *
 * A full rule definition containing rules for all supported rules looks like this:
 * ```js
 * module.exports = angularRule(function(context) {
 *   return {
 *     'angular?animation': function(configCallee, configFn) {},
 *     'angular?component': function(componentCallee, componentObj) {},
 *     'angular?config': function(configCallee, configFn) {},
 *     'angular?controller': function(controllerCallee, controllerFn) {},
 *     'angular?decorator': function(decoratorCallee, decoratorFn) {},
 *     'angular?directive': function(directiveCallee, directiveFn) {},
 *     'angular?factory': function(factoryCallee, factoryFn) {},
 *     'angular?filter': function(filterCallee, filterFn) {},
 *     'angular?inject': function(injectCallee, injectFn) {},  // inject() calls from angular-mocks
 *     'angular?run': function(runCallee, runFn) {},
 *     'angular?service': function(serviceCallee, serviceFn) {},
 *     'angular?provider': function(providerCallee, providerFn, provider$getFn) {}
 *   };
 * })
 * ```
 */
function angularRule(ruleDefinition) {
  var angularComponents;
  var angularModuleCalls;
  var angularModuleIdentifiers;
  var angularChainables;
  var injectCalls;

  return wrapper;

  function reset() {
    angularComponents = [];
    angularModuleCalls = [];
    angularModuleIdentifiers = [];
    angularChainables = [];
    injectCalls = [];
  }

  /**
   * A wrapper around the rule definition.
   */
  function wrapper(context) {
    reset();
    var ruleObject = ruleDefinition(context);
    injectCall(ruleObject, context, 'CallExpression:exit', checkCallee);
    injectCall(ruleObject, context, 'Program:exit', callAngularRules);
    return ruleObject;
  }

  /**
   * Makes sure an extra function gets called after custom defined rule has run.
   */
  function injectCall(ruleObject, context, propName, toCallAlso) {
    var original = ruleObject[propName];
    ruleObject[propName] = callBoth;

    function callBoth(node) {
      if (original) {
        original.call(ruleObject, node);
      }
      toCallAlso(ruleObject, context, node);
    }
  }

  /**
   * Collect expressions from an entire Angular module call chain expression statement and inject calls.
   *
   * This collects the following nodes:
   * ```js
   * angular.module()
   *         ^^^^^^
   * .animation('', function() {})
   *  ^^^^^^^^^     ^^^^^^^^^^
   * .component('', {})
   *  ^^^^^^^^^
   * .config(function() {})
   *  ^^^^^^ ^^^^^^^^^^
   * .constant()
   *  ^^^^^^^^
   * .controller('', function() {})
   *  ^^^^^^^^^^     ^^^^^^^^^^
   * .directive('', function() {})
   *  ^^^^^^^^^     ^^^^^^^^^^
   * .factory('', function() {})
   *  ^^^^^^^     ^^^^^^^^^^
   * .filter('', function() {})
   *  ^^^^^^     ^^^^^^^^^^
   * .provider('', function() {})
   *  ^^^^^^^^     ^^^^^^^^^^
   * .run('', function() {})
   *  ^^^     ^^^^^^^^^^
   * .service('', function() {})
   *  ^^^^^^^     ^^^^^^^^^^
   * .value();
   *  ^^^^^
   *
   * inject(function() {})
   * ^^^^^^ ^^^^^^^^^^
   * ```
   */
  function checkCallee(ruleObject, context, callExpressionNode) {
    // const text = context.getSourceCode().getText(callExpressionNode);
    // console.log(text);

    function getThisGuyRightHere() {
      return {
        callExpression: callExpressionNode,
        node: findInjectedArgument(callExpressionNode),
        scope: context.getScope(),
      };
    }

    var callee = callExpressionNode.callee;
    if (callee.type === 'Identifier') {
      const args = callExpressionNode.arguments;
      if ((callee.name === 'module' && args.every((x) => !!x) && args.length === 1) || args.length === 2) {
        const [moduleName, deps] = args;
        const isString = moduleName.type === 'Literal' && typeof moduleName.value === 'string';
        const isIdentifier = moduleName.type === 'Identifier';
        const isDepsArray = deps && deps.type === 'ArrayExpression';
        if ((isString || isIdentifier) && (!deps || isDepsArray)) {
          // module('stringliteral', [dep1, dep2])
          // ^^^^^^^^
          angularModuleCalls.push(callExpressionNode);
        }
      }

      const { parent } = callExpressionNode;
      if (callee.name === 'inject' && !(parent && parent.type === 'Decorator')) {
        // inject()
        // ^^^^^^
        injectCalls.push(getThisGuyRightHere());
      }
      return;
    }

    if (callee.type === 'MemberExpression') {
      const objName = callee.object.name;
      const propName = callee.property.name;

      if (objName === 'angular' && propName === 'module') {
        // angular.module()
        //         ^^^^^^
        angularModuleCalls.push(callExpressionNode);
      } else if (
        angularChainableNames.includes(propName) &&
        ((!!objName && objName.match(/[Mm]od(?:ule)$/)) ||
          angularModuleCalls.includes(callee.object) ||
          angularChainables.includes(callee.object))
      ) {
        // someVariableEndingInModule.controller()
        //                            ^^^^^^^^^^
        // angular.module().factory().controller()
        //                  ^^^^^^^   ^^^^^^^^^^
        angularChainables.push(callExpressionNode);
        angularComponents.push(getThisGuyRightHere());
      } else if (callee.object.type === 'Identifier') {
        // var app = angular.module(); app.factory()
        //                                 ^^^^^^^
        var scope = context.getScope();
        var isAngularModule = scope.variables.some(function (variable) {
          if (callee.object.name !== variable.name) {
            return false;
          }
          return variable.identifiers.some(function (id) {
            return angularModuleIdentifiers.indexOf(id) !== -1;
          });
        });
        if (isAngularModule) {
          angularChainables.push(callExpressionNode);
          angularComponents.push(getThisGuyRightHere());
        } else {
          return;
        }
      } else {
        return;
      }

      if (callExpressionNode.parent.type === 'VariableDeclarator') {
        // var app = angular.module()
        //     ^^^
        angularModuleIdentifiers.push(callExpressionNode.parent.id);
      }
    }
  }

  /**
   * Find the argument by an Angular component callee.
   */
  function findInjectedArgument(callExpressionNode) {
    const callee = callExpressionNode.callee;
    if (callee.type === 'Identifier') {
      return callExpressionNode.arguments[0];
    } else if (['run', 'config'].includes(callee.property.name)) {
      return callExpressionNode.arguments[0];
    } else {
      return callExpressionNode.arguments[1];
    }
  }

  /**
   * Call the Angular specific rules defined by the rule definition.
   */
  function callAngularRules(ruleObject, context) {
    angularComponents.forEach(function (component) {
      var name = component.callExpression.callee.property.name;
      var fn = ruleObject['angular?' + name];
      if (!fn) {
        return;
      }
      fn.apply(ruleObject, assembleArguments(component, context));
    });
    var injectRule = ruleObject['angular?inject'];
    if (injectRule) {
      injectCalls.forEach(function (thisGuy) {
        injectRule.call(ruleObject, thisGuy.callExpression.callee, thisGuy);
      });
    }
  }

  /**
   * Assemble the arguments for an Angular callee check.
   */
  function assembleArguments(thisGuy) {
    switch (thisGuy.callExpression.callee.property.name) {
      case 'animation':
      case 'component':
      case 'config':
      case 'controller':
      case 'decorator':
      case 'directive':
      case 'factory':
      case 'filter':
      case 'run':
      case 'service':
        return [thisGuy.callExpression.callee, thisGuy];
      case 'provider':
        return assembleProviderArguments(thisGuy);
    }
  }

  /**
   * Assemble arguments for a provider rule.
   *
   * On top of a regular Angular component rule, the provider rule gets called with the $get function as its 3rd argument.
   */
  function assembleProviderArguments(thisGuy) {
    return [thisGuy.callExpression, thisGuy, Object.assign({}, thisGuy, { fn: findProviderGet(thisGuy) })];
  }

  /**
   * Find the $get function of a provider based on the provider function body.
   */
  function findProviderGet(thisGuy) {
    let providerFn = thisGuy.node;
    if (providerFn && providerFn.type === 'Identifier') {
      providerFn = thisGuy.scope.variables.find((v) => v.name === providerFn.name).defs[0].node;
    }
    if (!providerFn) {
      return;
    }

    const class$get = providerFn.body.body.find((node) => node.type === 'MethodDefinition' && node.key.name === '$get');
    const obj$get = providerFn.body.body.find((node) => {
      const expr = node.expression;
      return (
        expr &&
        expr.type === 'AssignmentExpression' &&
        expr.left.type === 'MemberExpression' &&
        expr.left.property.name === '$get'
      );
    });

    const getFn = (class$get && class$get.value) || (obj$get && obj$get.expression.right);
    return getFn && getFn.type === 'ArrayExpression' ? getFn.elements[getFn.elements.length - 1] : getFn;
  }
}
