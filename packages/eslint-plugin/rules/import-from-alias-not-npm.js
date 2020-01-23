'use strict';
const { getSourceFileDetails, getImportFromNpm } = require('../utils/import-aliases');

/**
 * A group of rules that enforce spinnaker ES6 import alias conventions.
 *
 * Source code in a package (i.e., `core`) should not import from `@spinnaker/core`
 *
 * @version 0.1.0
 * @category conventions
 */
const rule = function(context) {
  const { ownPackage } = getSourceFileDetails(context.getFilename());
  if (!ownPackage) {
    return {};
  }

  return {
    ImportDeclaration: function(node) {
      if (node.source.type !== 'Literal' || !node.source.value) {
        return;
      }

      const importString = node.source.value;
      const importFromNpm = getImportFromNpm(importString);
      if (!importFromNpm || importFromNpm.pkg !== ownPackage) {
        return;
      }

      const { pkg, importPathWithSlash } = importFromNpm;
      const message =
        `Do not use ${importString} to import from ${ownPackage} from code inside ${ownPackage}. ` +
        ` Instead, use the ${pkg} alias or a relative import`;

      const fix = fixer => fixer.replaceText(node.source, `'${pkg}${importPathWithSlash}'`);
      context.report({ fix, node, message });
    },
  };
};

module.exports = {
  meta: {
    type: 'problem',
    docs: {
      description: `Enforces spinnaker ES6 import conventions for package aliases`,
    },
    fixable: 'code',
  },
  create: rule,
};
