import type { Rule } from 'eslint';
import type { ImportDeclaration } from 'estree';

import { getImportFromNpm, getSourceFileDetails } from '../utils/import-aliases';

/**
 * A group of rules that enforce spinnaker ES6 import alias conventions.
 *
 * Source code in a package (i.e., `core`) should not import from `@spinnaker/core`
 *
 * @version 0.1.0
 * @category conventions
 */
const rule = function (context: Rule.RuleContext) {
  const { ownPackage } = getSourceFileDetails(context.getFilename());
  if (!ownPackage) {
    return {};
  }

  return {
    ImportDeclaration(node: ImportDeclaration) {
      if (node.source.type !== 'Literal' || !node.source.value) {
        return;
      }
      const importString = node.source.value as string;
      const importFromNpm = getImportFromNpm(importString);
      if (!importFromNpm || importFromNpm.pkg !== ownPackage) {
        return;
      }

      const { pkg, importPathWithSlash } = importFromNpm;
      const message =
        `Do not use ${importString} to import from ${ownPackage} from code inside ${ownPackage}. ` +
        ` Instead, use the ${pkg} alias or a relative import`;

      const fix = (fixer) => fixer.replaceText(node.source, `'${pkg}${importPathWithSlash}'`);
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
