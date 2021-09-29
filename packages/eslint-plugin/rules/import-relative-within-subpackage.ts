import type { Rule } from 'eslint';
import type { ImportDeclaration } from 'estree';
import path from 'path';

import { getAliasImport, getAllSpinnakerPackages, getSourceFileDetails } from '../utils/import-aliases';

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
const rule = function (context: Rule.RuleContext) {
  const sourceFile = context.getFilename();
  const { modulesPath, ownPackage, ownSubPackage, filePath } = getSourceFileDetails(sourceFile);
  if (!ownPackage) {
    return {};
  }
  const allSpinnakerPackages = getAllSpinnakerPackages(modulesPath);

  return {
    ImportDeclaration: function (node: ImportDeclaration & Rule.NodeParentExtension) {
      if (node.source.type !== 'Literal' || !node.source.value) {
        return;
      }

      const importString = node.source.value as string;
      const aliasImport = getAliasImport(allSpinnakerPackages, importString);
      if (!aliasImport || aliasImport.pkg !== ownPackage || aliasImport.subPkg !== ownSubPackage) {
        return;
      }

      const { pkg, subPkg, importPath } = aliasImport;

      const message =
        `Do not use an alias to import from ${pkg}/${subPkg} from code inside ${pkg}/${subPkg}.` +
        ` Instead, use a relative import`;

      const fix = (fixer: Rule.RuleFixer) => {
        const relativeDir = path.relative(path.dirname(filePath), path.dirname(importPath)) || '.';
        let newPath = path.join(relativeDir, path.basename(importPath));
        newPath = newPath.match(/^\.?\.\//) ? newPath : './' + newPath;
        return fixer.replaceText(node.source, `'${newPath}'`);
      };

      if (aliasImport.importPath === aliasImport.subPkg) {
        // Do not try to fix: import from 'alias/subpkg'
        context.report({ node, message });
      } else {
        // Do try to fix: import from 'alias/subpkg/nestedimport'
        context.report({ fix, node, message });
      }
    },
  };
};

const importAliasesRule: Rule.RuleModule = {
  meta: {
    type: 'problem',
    docs: {
      description: `Enforces spinnaker ES6 import conventions for package aliases`,
    },
    fixable: 'code',
  },
  create: rule,
};
export default importAliasesRule;
