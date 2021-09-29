import type { Rule } from 'eslint';
import type { CallExpression, ImportDeclaration } from 'estree';
import _ from 'lodash';
import { isLiteral } from '../utils/utils';

/**
 * react2angular: Always wrap react components in an error boundary
 * Uses withErrorBoundary from core/presentation
 * @version 0.1.0
 */
const rule = function (context: Rule.RuleContext) {
  let coreImport: ImportDeclaration;

  return {
    // Find an import from @spinnaker/core or core/presentation
    // This will be used to add the import for withErrorBoundary
    ImportDeclaration: function (node: ImportDeclaration) {
      // import { foo, bar } from 'package';
      //                           ^^^^^^^
      const from = node.source.value || '';
      if (from === '@spinnaker/core') {
        coreImport = node;
      }
    },
    CallExpression: function (node: CallExpression & Rule.NodeParentExtension) {
      // Find:
      // react2angular(SomeComponent, ...)
      const match = {
        type: 'CallExpression',
        callee: {
          type: 'Identifier',
          name: 'react2angular',
        },
      };

      if (!_.isMatch(node, match)) {
        return;
      }

      const r2aComponent = node.arguments[0];

      const wrappedInErrorBoundaryMatch = {
        type: 'CallExpression',
        callee: {
          type: 'Identifier',
          name: 'withErrorBoundary',
        },
      };

      // The react2angular component is already wrapped, nice!
      if (_.isMatch(r2aComponent, wrappedInErrorBoundaryMatch)) {
        return;
      }

      const message = `Wrap react2angular components in an error boundary using 'withErrorBoundary()'`;
      const filename = context.getFilename();
      const originalComponentSrc = context.getSourceCode().getText(r2aComponent);

      // Try to determine the angularjs component name
      // Look for component('angularComponentName', react2angular(ReactComponent, ....))
      //                    ^^^^^^^^^^^^^^^^^^^^^^

      const parentMatch = {
        type: 'CallExpression',
        callee: {
          property: {
            type: 'Identifier',
            name: 'component',
          },
        },
      };
      const isComponentCallExpression = (node): node is CallExpression => _.isMatch(node, parentMatch);

      let componentName = `'react2angular component'`;
      const parentNode = node.parent;
      if (isComponentCallExpression(parentNode)) {
        const [componentNameLiteralNode, arg2] = parentNode.arguments || [];
        if (arg2 === node && isLiteral(componentNameLiteralNode)) {
          componentName = componentNameLiteralNode.raw;
        }
      }

      const fix = (fixer: Rule.RuleFixer) => {
        const wrapped = `withErrorBoundary(${originalComponentSrc}, ${componentName})`;
        const insertErrorBoundary = fixer.replaceText(r2aComponent, wrapped);

        const fixes = [insertErrorBoundary];

        if (coreImport && coreImport.specifiers.length > 0) {
          // Append to the existing core/presentation or @spinnaker/core import
          const lastImport = coreImport.specifiers[coreImport.specifiers.length - 1];
          fixes.push(fixer.insertTextAfter(lastImport, `, withErrorBoundary`));
        } else {
          const importString = filename.includes('/modules/core/')
            ? 'core/presentation/SpinErrorBoundary'
            : '@spinnaker/core';
          fixes.push(fixer.insertTextBeforeRange([0, 0], `import { withErrorBoundary } from '${importString}';\n`));
        }
        return fixes;
      };

      context.report({ message, node, fix });
    },
  };
};

const ruleModule: Rule.RuleModule = {
  meta: {
    type: 'problem',
    docs: {
      description: `react2angular: Always wrap react components in an error boundary`,
    },
    fixable: 'code',
  },
  create: rule,
};

export default ruleModule;
