const fs = require('node:fs');
const path = require('node:path');

const colorMap = require('@spinnaker/styleguide/src/colorMap');
const remapping = require('@ampproject/remapping');
const autoprefixer = require('autoprefixer');
const MagicString = require('magic-string');
const postCssColorFix = require('postcss-colorfix');
const postCssUrl = require('postcss-url');
const { visualizer } = require('rollup-plugin-visualizer');
const svgr = require('@svgr/rollup');

const IMPORTED_ASSET_LIMIT = 24000;
const IMPORTED_ASSET_PATTERN = /\.(?:gif|html|jpe?g|png|svg|webp)$/i;
const ASSET_MIME_TYPES = {
  '.gif': 'image/gif',
  '.html': 'text/html',
  '.jpeg': 'image/jpeg',
  '.jpg': 'image/jpeg',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.webp': 'image/webp',
};

function generatedLineSourceMap(code, source, sourceContent) {
  return {
    version: 3,
    names: [],
    sources: [source],
    sourcesContent: [sourceContent],
    // SVGR exposes no XML token map, so consistently map each generated line to the source SVG's first line.
    mappings: code
      .split('\n')
      .map(() => 'AAAA')
      .join(';'),
  };
}

function importedJsAssetPlugin() {
  return {
    name: 'spinnaker-imported-js-assets',
    enforce: 'pre',
    load(id) {
      if (id.includes('?')) return null;
      const file = id;
      if (!IMPORTED_ASSET_PATTERN.test(file)) return null;

      const source = fs.readFileSync(file);
      if (source.length <= IMPORTED_ASSET_LIMIT) {
        const mimeType = ASSET_MIME_TYPES[path.extname(file).toLowerCase()];
        const dataUrl = `data:${mimeType};base64,${source.toString('base64')}`;
        const code = `export default ${JSON.stringify(dataUrl)};`;
        const map = file.endsWith('.svg')
          ? generatedLineSourceMap(code, file, source.toString('utf8'))
          : { mappings: '' };
        return { code, map };
      }

      const referenceId = this.emitFile({ type: 'asset', name: path.basename(file), source });
      const code = `export default import.meta.ROLLUP_FILE_URL_${referenceId};`;
      return {
        code,
        map: file.endsWith('.svg') ? generatedLineSourceMap(code, file, source.toString('utf8')) : { mappings: '' },
      };
    },
  };
}

function sourceMappedSvgrPlugin(options) {
  const delegate = svgr(options);
  return {
    ...delegate,
    async transform(code, id) {
      const result = await delegate.transform.call(this, code, id);
      if (!result) return result;

      return {
        ...result,
        map: generatedLineSourceMap(result.code, id, code),
      };
    },
  };
}

function cssInjectedByJsPlugin() {
  return {
    name: 'spinnaker-css-injected-by-js',
    apply: 'build',
    enforce: 'post',
    generateBundle(outputOptions, bundle) {
      const cssAssets = Object.entries(bundle).filter(
        ([fileName, output]) => output.type === 'asset' && fileName.endsWith('.css'),
      );
      if (!cssAssets.length) return;

      const css = cssAssets
        .map(([, asset]) =>
          asset.source instanceof Uint8Array ? new TextDecoder().decode(asset.source) : String(asset.source),
        )
        .join('');
      cssAssets.forEach(([fileName]) => delete bundle[fileName]);

      Object.values(bundle)
        .filter((output) => output.type === 'chunk' && output.isEntry)
        .forEach((chunk) => {
          const injection = `(()=>{try{if(typeof document!="undefined"){var elementStyle=document.createElement("style");elementStyle.appendChild(document.createTextNode(${JSON.stringify(
            css,
          )}));document.head.appendChild(elementStyle)}}catch(error){console.error("spinnaker-css-injected-by-js",error)}})()`;
          const transformed = new MagicString(chunk.code);
          transformed.prepend(`${injection};\n`);
          const injectionMap = transformed.generateMap({
            file: chunk.fileName,
            hires: true,
            includeContent: true,
            source: chunk.fileName,
          });
          chunk.code = transformed.toString();
          chunk.map = chunk.map ? remapping([injectionMap, chunk.map], () => null) : injectionMap;
          const mapAsset = chunk.sourcemapFileName && bundle[chunk.sourcemapFileName];
          if (mapAsset?.type === 'asset') mapAsset.source = JSON.stringify(chunk.map);
          if (chunk.viteMetadata) chunk.viteMetadata.importedCss = new Set();
        });
    },
  };
}

function createPackageExternal(packageSections, extraSpecifiers = []) {
  const sections = Array.isArray(packageSections) ? packageSections : [packageSections];
  const packageNames = [...sections.flatMap((section) => Object.keys(section || {})), ...extraSpecifiers];
  if (!packageNames.length) return () => false;

  const escapedNames = packageNames.map((packageName) => packageName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));
  const packagePattern = new RegExp(`^(?:${escapedNames.join('|')})(?:/|$)`);
  return (id) => packagePattern.test(id);
}

async function createViteLibraryConfig(options = {}) {
  const root = options.root || process.cwd();
  const packageJson = options.packageJson || JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'));
  const nodeEnvironment = process.env.NODE_ENV || 'development';
  const environmentMinify = process.env.ROLLUP_MINIFY;
  const shouldMinify =
    environmentMinify === 'true' || (nodeEnvironment === 'production' && environmentMinify !== 'false');
  const defaultPlugins = [importedJsAssetPlugin(), sourceMappedSvgrPlugin(), cssInjectedByJsPlugin()];
  const plugins = [...(options.plugins ?? defaultPlugins)];
  if (process.env.ROLLUP_STATS) plugins.push(visualizer({ sourcemap: true }));

  return {
    root,
    define: {
      'process.env.NODE_ENV': JSON.stringify(nodeEnvironment),
    },
    build: {
      lib: {
        entry: options.entry ?? path.join(root, 'src/index.ts'),
        formats: ['es'],
        cssFileName: 'style.css',
      },
      outDir: options.outDir ?? path.join(root, 'dist'),
      sourcemap: true,
      target: 'es2018',
      emptyOutDir: false,
      cssCodeSplit: false,
      minify: shouldMinify ? 'terser' : false,
      terserOptions: {
        format: {
          comments: (node, comment) =>
            comment.type === 'comment2' &&
            /@preserve|@license|@cc_on|webpackChunkName|webpackIgnore|vite-ignore/i.test(comment.value),
        },
      },
      rollupOptions: {
        external: options.external ?? createPackageExternal(packageJson.dependencies),
        output: {
          entryFileNames: 'index.js',
          chunkFileNames: '[name]-[hash].js',
          assetFileNames: '[name][hash][extname]',
        },
      },
    },
    css: {
      postcss: {
        plugins: [autoprefixer(), postCssColorFix({ colors: colorMap }), postCssUrl({ url: 'inline' })],
      },
    },
    ...(options.afterDeclarations ? { spinnaker: { afterDeclarations: options.afterDeclarations } } : {}),
    plugins,
  };
}

async function viteConfig() {
  return createViteLibraryConfig();
}

module.exports = Object.assign(viteConfig, { createPackageExternal, createViteLibraryConfig });
