import type { Rule } from 'eslint';
import type { ImportDeclaration } from 'estree';

import { getRelativeImport, getSourceFileDetails } from '../utils/import-aliases';

/**
 * A group of rules that enforce spinnaker ES6 import alias conventions.
 *
 * Source code in a package (i.e., `amazon`) should not import from package (i.e., `core`) using a relative path.
 * Instead, it should import from `@spinnaker/core`
 *
 * @version 0.1.0
 * @category conventions
 */
const rule = function (context: Rule.RuleContext) {
  const sourceFile = context.getFilename();
  const { modulesPath, sourceDirectory, ownPackage } = getSourceFileDetails(sourceFile);
  if (!ownPackage) {
    return {};
  }

  return {
    ImportDeclaration: function (node: ImportDeclaration) {
      if (node.source.type !== 'Literal' || !node.source.value) {
        return;
      }

      const importString = node.source.value as string;
      const relativeImport = getRelativeImport(sourceDirectory, modulesPath, importString);

      if (!relativeImport || relativeImport.pkg === ownPackage) {
        return;
      }

      const { pkg } = relativeImport;
      const message =
        `Do not use a relative import to import from ${pkg} from code inside ${ownPackage}.` +
        ` Instead, use the npm package @spinnaker/${pkg}`;

      const fix = (fixer) => fixer.replaceText(node.source, `'@spinnaker/${pkg}'`);
      context.report({ fix, node, message });
    },
  };
};

const ruleModule: Rule.RuleModule = {
  meta: {
    type: 'problem',
    docs: {
      description: `Enforces spinnaker ES6 import conventions for package aliases`,
    },
    fixable: 'code',
  },
  create: rule,
};

export default ruleModule;
