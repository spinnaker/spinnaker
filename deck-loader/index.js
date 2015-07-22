'use strict';

var esprima = require('esprima');
var walk = require('esprima-walk').walkAddParent;
var escodegen = require('escodegen');

function getTopLevelModuleExpression(node) {
  for (;;) {
    if (node.parent.type === 'MemberExpression' ||
        node.parent.type === 'CallExpression') {
      node = node.parent;
    } else {
      break;
    }
  }
  return node;
}

function isAngularModuleDeclaration(node) {
  return node.type === 'CallExpression'&&
    node.callee && node.callee.object &&
    node.callee.object.name === 'angular' &&
    node.callee.property.name === 'module' &&
    node.arguments.length === 2 &&
    node.arguments[1].type === 'ArrayExpression';
}

module.exports = function(source, inputSourceMap) {
  this.cacheable();
  var sourceModified = false;
  var ast = esprima.parse(source, {
    loc: true,
  });
  walk(ast, function(node) {
    if (!sourceModified && isAngularModuleDeclaration(node)) {
      var moduleNode = getTopLevelModuleExpression(node);
      var nameNode = {
        type: 'MemberExpression',
        computed: false,
        object: moduleNode,
        property: {
          type: 'Identifier',
          name: 'name',
        },
      };

      if (moduleNode.parent.type === 'AssignmentExpression') {
        moduleNode.parent.right = nameNode;
        sourceModified = true;
      } else {
        this.emitError('Failed to parse angular module declaration for ' + this.resource);
      }
    }
  });

  if (sourceModified) {
    var output = escodegen.generate(ast, {
      sourceMap: true,
      sourceMapWithCode: true,
    });
    // TODO: generate a new source map using inputSourceMap.
    this.callback(null, output.code, inputSourceMap);
  } else {
    this.emitWarning('No angular module found in ' + this.resource);
    this.callback(null, source, inputSourceMap);
  }
};
