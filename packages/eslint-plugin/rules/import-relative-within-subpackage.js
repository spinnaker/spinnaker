'use strict';
const path = require('path');
const { getAliasImport, getSourceFileDetails, getAllSpinnakerPackages } = require('../utils/import-aliases');

/**
 * A group of rules that enforce spinnaker ES6 import alias conventions.
 *
 * Source code in a package (i.e., `core/presentation` should not import from the same subpackage using an alias
 * `core/presentation`.
 * Instead, it should import relatively `../../path/file`
 *
 * @version 0.1.0
 * @category conventions
 */
const rule = function(context) {
  const sourceFile = context.getFilename();
  const { modulesPath, ownPackage, ownSubPackage, filePath } = getSourceFileDetails(sourceFile);
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
      if (
        !aliasImport ||
        aliasImport.pkg !== ownPackage ||
        aliasImport.subPkg !== ownSubPackage ||
        aliasImport.importPath === aliasImport.subPkg // don't handle import from 'core/subpackage' in this rule
      ) {
        return;
      }

      const { pkg, subPkg, importPath } = aliasImport;

      const message =
        `Do not use an alias to import from ${pkg}/${subPkg} from code inside ${pkg}/${subPkg}.` +
        ` Instead, use a relative import`;
      const fix = fixer => {
        const relativeDir = path.relative(path.dirname(filePath), path.dirname(importPath)) || '.';
        let newPath = path.join(relativeDir, path.basename(importPath));
        newPath = newPath.match(/^\.?\.\//) ? newPath : './' + newPath;
        return fixer.replaceText(node.source, `'${newPath}'`);
      };
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
