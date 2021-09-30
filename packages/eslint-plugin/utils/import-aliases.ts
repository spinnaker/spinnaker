import * as fs from 'fs';
import { flattenDeep, memoize } from 'lodash';
import * as path from 'path';

export function locateSourceFile(modulesPath: string, moduleName: string, importPath = '') {
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

  return flattenDeep<string>(paths).find((p) => fs.existsSync(p));
}

function _getAllSpinnakerPackages(modulesPath: string) {
  const paths = fs.readdirSync(modulesPath);
  return paths
    .map((file) => path.join(modulesPath, file))
    .filter((child) => fs.statSync(child).isDirectory())
    .map((packagePath) => packagePath.split('/').pop());
}

export const getAllSpinnakerPackages = memoize(_getAllSpinnakerPackages);

function makeResult(pkg: string, importPath: string) {
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
export function getImportFromNpm(importString: string) {
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
export function getAliasImport(allSpinnakerPackages: string[], importString: string) {
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
export function getRelativeImport(sourceFileName: string, modulesPath: string, importString: string) {
  if (!importString.startsWith('../')) {
    return undefined;
  }

  const resolvedPath = path.resolve(sourceFileName, importString);
  const maybeImport = path.relative(modulesPath, resolvedPath);
  const [pkg, ...rest] = maybeImport.split(path.sep);

  return pkg ? makeResult(pkg, rest.join('/')) : undefined;
}

function _getSourceFileDetails(sourceFile: string) {
  const [, modulesPath, ownPackage, filePath] = /^(.*packages)\/([^/]+)\/(?:src\/)?(.*)$/.exec(sourceFile) || [];
  const ownSubPackage = getSubPackage(ownPackage, filePath);
  const sourceDirectory = path.resolve(sourceFile, '..');
  return { modulesPath, sourceDirectory, ownPackage, ownSubPackage, filePath };
}

function getSubPackage(_packageName: string, filePath: string) {
  const [, subPkg] = /^([^/]+)\/?.*/.exec(filePath) || [];
  return subPkg;
}

export const getSourceFileDetails = memoize(_getSourceFileDetails);
