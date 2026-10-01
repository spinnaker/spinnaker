const fs = require('node:fs');
const path = require('node:path');

const MagicString = require('magic-string');
const { createPackageExternal, createViteLibraryConfig } = require('@spinnaker/scripts/config/vite.config.base.module');

const SHARED_LIBRARY_GLOBAL_PREFIX = 'spinnaker.plugins.sharedLibraries';
const SHARED_LIBRARY_SPECIFIERS = [
  'ajv',
  '@spinnaker/core',
  '@spinnaker/kayenta',
  '@uirouter/core',
  '@uirouter/react',
  '@uirouter/rx',
  'lodash',
  'prop-types',
  'react',
  'react-dom',
  'react-redux',
  'redux-actions',
  'reselect',
  'rxjs',
  'rxjs/Observable',
];
const COMMONJS_SUFFIXES = ['?commonjs-proxy', '?commonjs-require', '?commonjs-external'];

function sharedLibraryGlobal(specifier) {
  const withoutVirtualPrefix = specifier.startsWith('\0') ? specifier.slice(1) : specifier;
  const library = COMMONJS_SUFFIXES.reduce(
    (candidate, suffix) => (candidate.endsWith(suffix) ? candidate.slice(0, -suffix.length) : candidate),
    withoutVirtualPrefix,
  );
  if (!SHARED_LIBRARY_SPECIFIERS.includes(library)) return undefined;
  return `${SHARED_LIBRARY_GLOBAL_PREFIX}.${library.replace(/[^\w]/g, '_')}`;
}

function sharedLibraryValue(globalExpression, property) {
  const propertyName = property?.name ?? property?.value;
  if (!property || propertyName === 'default') return globalExpression;
  if (property.type === 'Identifier') return `${globalExpression}.${property.name}`;
  return `${globalExpression}[${JSON.stringify(propertyName)}]`;
}

function importReplacement(node, globalExpression) {
  return node.specifiers
    .map((specifier) => {
      const property = specifier.type === 'ImportSpecifier' ? specifier.imported : undefined;
      const value = sharedLibraryValue(globalExpression, property);
      return `const ${specifier.local.name} = ${value};`;
    })
    .join('\n');
}

function exportName(exported) {
  return exported.type === 'Identifier' ? exported.name : JSON.stringify(exported.value);
}

function exportReplacement(node, globalExpression, index, allocateBinding) {
  return node.specifiers
    .map((specifier, specifierIndex) => {
      const value = sharedLibraryValue(globalExpression, specifier.local);
      const local = allocateBinding(`__spinnaker_shared_${index}_${specifierIndex}`);
      const exported = exportName(specifier.exported);
      return `const ${local} = ${value}; export { ${local} as ${exported} };`;
    })
    .join('\n');
}

function visitAst(node, visitor) {
  if (!node || typeof node !== 'object') return;
  visitor(node);
  for (const value of Object.values(node)) {
    if (Array.isArray(value)) value.forEach((child) => visitAst(child, visitor));
    else visitAst(value, visitor);
  }
}

function createSharedLibraryGlobalsPlugin() {
  return {
    name: 'spinnaker-shared-library-globals',
    enforce: 'post',
    options(inputOptions) {
      const plugins = Array.isArray(inputOptions.plugins)
        ? [...inputOptions.plugins]
        : inputOptions.plugins
        ? [inputOptions.plugins]
        : [];
      plugins.unshift({
        name: 'spinnaker-shared-library-globals-resolver',
        resolveId(importee, _importer, options) {
          if (importee.startsWith('\0') || options.isEntry) return null;
          return sharedLibraryGlobal(importee) ? false : null;
        },
      });
      return { ...inputOptions, plugins };
    },
    transform(source, id) {
      if (!SHARED_LIBRARY_SPECIFIERS.some((specifier) => source.includes(specifier))) return undefined;

      const ast = this.parse(source);
      const transformed = new MagicString(source);
      const usedIdentifiers = new Set();
      visitAst(ast, (node) => {
        if (node.type === 'Identifier') usedIdentifiers.add(node.name);
      });
      const allocateBinding = (preferredName) => {
        let suffix = 0;
        let binding = preferredName;
        while (usedIdentifiers.has(binding)) binding = `${preferredName}_${++suffix}`;
        usedIdentifiers.add(binding);
        return binding;
      };
      let changed = false;
      ast.body.forEach((node, index) => {
        if (node.type === 'ImportDeclaration') {
          const globalExpression = sharedLibraryGlobal(node.source.value);
          if (!globalExpression) return;
          transformed.overwrite(node.start, node.end, importReplacement(node, globalExpression));
          changed = true;
          return;
        }
        if (node.type === 'ExportNamedDeclaration' && node.source) {
          const globalExpression = sharedLibraryGlobal(node.source.value);
          if (!globalExpression) return;
          transformed.overwrite(
            node.start,
            node.end,
            exportReplacement(node, globalExpression, index, allocateBinding),
          );
          changed = true;
          return;
        }
        if (node.type === 'ExportAllDeclaration') {
          const globalExpression = sharedLibraryGlobal(node.source.value);
          if (!globalExpression) return;
          if (!node.exported) {
            this.error(`Cannot export all properties from shared runtime library ${node.source.value}`, node.start);
          }
          const local = allocateBinding(`__spinnaker_shared_${index}_namespace`);
          transformed.overwrite(
            node.start,
            node.end,
            `const ${local} = ${globalExpression}; export { ${local} as ${exportName(node.exported)} };`,
          );
          changed = true;
        }
      });
      visitAst(ast, (node) => {
        if (node.type !== 'ImportExpression' || typeof node.source?.value !== 'string') return;
        const globalExpression = sharedLibraryGlobal(node.source.value);
        if (!globalExpression) return;
        transformed.overwrite(node.start, node.end, `Promise.resolve(${globalExpression})`);
        changed = true;
      });

      if (!changed) return undefined;
      return {
        code: transformed.toString(),
        map: transformed.generateMap({ hires: true, includeContent: true, source: id }),
      };
    },
  };
}

async function pluginViteConfig() {
  const root = process.cwd();
  const packageJson = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'));
  const config = await createViteLibraryConfig({
    root,
    packageJson,
    external: createPackageExternal([packageJson.dependencies, packageJson.peerDependencies]),
    outDir: path.join(root, 'build/dist'),
  });
  config.plugins.push(createSharedLibraryGlobalsPlugin());
  return config;
}

module.exports = Object.assign(pluginViteConfig, { createSharedLibraryGlobalsPlugin });
