import type { TSESTree } from '@typescript-eslint/types';
import type { Rule } from 'eslint';
import type { ImportDeclaration } from 'estree';
import _ from 'lodash';

/**
 * No slashes in string literals passed to API.one() / API.all()
 *
 * @version 0.1.0
 * @category
 */
const rule = function (context: Rule.RuleContext) {
  return {
    TSTypeReference: function (_node) {
      const node = _node as TSESTree.TSTypeReference;
      // var foo: IPromise<any> = bar()
      //          ^^^^^^^^
      const type_IPromise = {
        type: 'TSTypeReference',
        typeName: {
          type: 'Identifier',
          name: 'IPromise',
        },
      };

      // var foo: ng.IPromise<any> = bar()
      //          ^^^^^^^^^^^
      const type_ng_IPromise = {
        type: 'TSTypeReference',
        typeName: {
          type: 'TSQualifiedName',
          left: {
            type: 'Identifier',
            name: 'ng',
          },
          right: {
            type: 'Identifier',
            name: 'IPromise',
          },
        },
      };

      const message = `Prefer using PromiseLike type instead of AngularJS IPromise.`;
      const fix = (fixer) => fixer.replaceText(node.typeName, 'PromiseLike');
      if (_.isMatch(node, type_IPromise)) {
        context.report({ fix, node: node.typeName as Rule.Node, message });
      } else if (_.isMatch(node, type_ng_IPromise)) {
        context.report({ fix, node: node.typeName as Rule.Node, message });
      }
    },

    // If there are any unused IPromise imports, remove them
    ImportDeclaration: function (node: ImportDeclaration) {
      const importIPromise = {
        type: 'ImportSpecifier',
        imported: {
          type: 'Identifier',
          name: 'IPromise',
        },
      };

      const message = `Unused IPromise import`;

      // import { foo, IPromise, bar } from 'angular';
      //               ^^^^^^^^
      const specifiers = node.specifiers || [];
      const foundIPromiseImport = specifiers.find((s) => _.isMatch(s, importIPromise));

      const variables = context.getScope().variables;
      const variable = variables.find((x) => x.defs.some((def) => def.node === foundIPromiseImport));
      const unused = variable && variable.references.length === 0;

      const fix = (fixer: Rule.RuleFixer) => {
        const importCount = node.specifiers.length;
        if (importCount === 1) {
          // Delete the whole import
          return fixer.replaceText(node, '');
        } else {
          // Delete only IPromise from the import
          const source = context
            .getSourceCode()
            .getText(node)
            .replace(/,\s*IPromise/g, '')
            .replace(/IPromise\s*,\s*/g, '');

          return fixer.replaceText(node, source);
        }
      };

      if (foundIPromiseImport && unused) {
        context.report({ node, message, fix });
      }
    },
  };
};

const ruleModule: Rule.RuleModule = {
  meta: {
    type: 'problem',
    docs: {
      description: ``,
    },
    fixable: 'code',
  },
  create: rule,
};

export default ruleModule;
