'use strict';
const { getAliasImport, getSourceFileDetails, getAllSpinnakerPackages } = require('../utils/import-aliases');

/**
 * A group of rules that enforce spinnaker ES6 import alias conventions.
 *
 * Source code in a package (i.e., `amazon`) should not import from a different package using an alias (i.e., `core/`)
 * Instead, it should import from `@spinnaker/core`
 *
 * @version 0.1.0
 * @category conventions
 */
const rule = function(context) {
  const sourceFile = context.getFilename();
  const { modulesPath, ownPackage } = getSourceFileDetails(sourceFile);
  if (!ownPackage) {
    return {};
  }
  const allSpinnakerPackages = getAllSpinnakerPackages(modulesPath);

  return {
    ImportDeclaration: function(node) {
      if (node.source.type !== 'Literal' || !node.source.value) {
        return;
      }

      const importString = node.source.value;
      const aliasImport = getAliasImport(allSpinnakerPackages, importString);

      if (!aliasImport || aliasImport.pkg === ownPackage) {
        return;
      }
      const { pkg } = aliasImport;
      const message =
        `Do not use an alias to import from ${pkg} from code inside ${ownPackage}.` +
        ` Instead, use the npm package @spinnaker/${pkg}`;

      const fix = fixer => fixer.replaceText(node.source, `'@spinnaker/${pkg}'`);
      context.report({ fix, node, message });
    },
  };
};

const importAliasesRule = (module.exports = {
  meta: {
    type: 'problem',
    docs: {
      description: `Enforces spinnaker ES6 import conventions for package aliases`,
    },
    fixable: 'code',
  },
  create: rule,
});
