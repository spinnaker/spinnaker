const path = require('path');
const fs = require('fs');
const { flattenDeep, memoize } = require('lodash');

function locateSourceFile(modulesPath, moduleName, importPath = '') {
  const srcPrefixes = ['src', ''];
  const indexFiles = ['', 'index'];
  const extensions = ['.ts', '.tsx', '.js', '.jsx'];

  const paths = srcPrefixes.map((prefix) =>
    extensions.map((extension) =>
      indexFiles.map((indexFile) => {
        return path.join(modulesPath, moduleName, prefix, importPath, indexFile) + extension;
      }),
    ),
  );

  return flattenDeep(paths).find((p) => fs.existsSync(p));
}

function _getAllSpinnakerPackages(modulesPath) {
  const paths = fs.readdirSync(modulesPath);
  return paths
    .map((file) => path.join(modulesPath, file))
    .filter((child) => fs.statSync(child).isDirectory())
    .map((packagePath) => packagePath.split('/').pop());
}

const getAllSpinnakerPackages = memoize(_getAllSpinnakerPackages);

function makeResult(pkg, importPath) {
  const subPkg = getSubPackage(pkg, importPath);
  importPath = importPath || '';
  const importPathWithSlash = importPath ? '/' + importPath : '';
  return pkg ? { pkg, subPkg, importPath, importPathWithSlash } : undefined;
}

/**
 * Given '@spinnaker/amazon', returns { pkg: 'amazon', path: undefined };
 * Given '@spinnaker/core/deep/import', returns { pkg: 'core', path: 'deep/import' };
 * Given 'anythingelse', returns undefined
 */
function getImportFromNpm(importString) {
  const regexp = new RegExp(`^@spinnaker/([^/]+)(/.*)?$`);
  const [, pkg, importPath] = regexp.exec(importString) || [];
  return makeResult(pkg, importPath);
}

/**
 * If code imports from a known spinnaker package alias
 * Given 'amazon', returns { pkg: 'amazon', path: undefined };
 * Given 'core/deep/import', returns { pkg: 'core', path: 'deep/import' };
 * Given 'nonspinnakerpackage/deep/import', returns undefined
 */
function getAliasImport(allSpinnakerPackages, importString) {
  const [, pkg, importPath] = /^([^/]+)\/(.*)$/.exec(importString) || [];
  return allSpinnakerPackages.includes(pkg) ? makeResult(pkg, importPath) : undefined;
}

/**
 * If code imports from .. relatively, returns the potential alias
 * Assume all examples are from a file /packages/core/subdir/file.ts
 * Given '../../amazon/loadbalancers/loadbalancer', returns { pkg: 'amazon', path: 'loadbalancers/loadbalancer' };
 * Given '../widgets/button', returns { pkg: 'core', path: 'widgets/button' };
 * Given './file2', returns { pkg: 'core', path: 'subdir/file2' };
 */
function getRelativeImport(sourceFileName, modulesPath, importString) {
  if (!importString.startsWith('../')) {
    return undefined;
  }

  const resolvedPath = path.resolve(sourceFileName, importString);
  const maybeImport = path.relative(modulesPath, resolvedPath);
  const [pkg, ...rest] = maybeImport.split(path.sep);

  return pkg ? makeResult(pkg, rest.join('/')) : undefined;
}

function _getSourceFileDetails(sourceFile) {
  const [, modulesPath, ownPackage, filePath] =
    /^(.*app\/scripts\/modules)\/([^/]+)\/(?:src\/)?(.*)$/.exec(sourceFile) || [];
  const ownSubPackage = getSubPackage(ownPackage, filePath);
  const sourceDirectory = path.resolve(sourceFile, '..');
  return { modulesPath, sourceDirectory, ownPackage, ownSubPackage, filePath };
}

function getSubPackage(packageName, filePath) {
  const [, subPkg] = /^([^/]+)\/?.*/.exec(filePath) || [];
  return subPkg;
}

const getSourceFileDetails = memoize(_getSourceFileDetails);

module.exports = {
  getAliasImport,
  getAllSpinnakerPackages,
  getImportFromNpm,
  getRelativeImport,
  getSourceFileDetails,
  locateSourceFile,
};
