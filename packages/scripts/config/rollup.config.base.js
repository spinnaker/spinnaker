const colorMap = require('@spinnaker/styleguide/src/colorMap');
const { nodeResolve } = require('@rollup/plugin-node-resolve');
const commonjs = require('@rollup/plugin-commonjs');
const json = require('@rollup/plugin-json');
const postCss = require('rollup-plugin-postcss');
const replace = require('@rollup/plugin-replace');
const { terser } = require('rollup-plugin-terser');
const typescript = require('@rollup/plugin-typescript');
const url = require('@rollup/plugin-url');
const svgr = require('@svgr/rollup').default;
const autoPrefixer = require('autoprefixer');
const postCssColorFix = require('postcss-colorfix');
const postCssNested = require('postcss-nested');
const postCssUrl = require('postcss-url');
const { visualizer } = require('rollup-plugin-visualizer');

const ROLLUP_STATS = !!process.env.ROLLUP_STATS;
const ROLLUP_WATCH = !!process.env.ROLLUP_WATCH;
const NODE_ENV = JSON.stringify(process.env.NODE_ENV || 'development');
const ENV_MINIFY = process.env.ROLLUP_MINIFY;
const ROLLUP_MINIFY = ENV_MINIFY === 'true' || (NODE_ENV === '"production"' && ENV_MINIFY !== 'false');

// eslint-disable-next-line no-console
console.log({ ROLLUP_STATS, ROLLUP_WATCH, ROLLUP_MINIFY, NODE_ENV });

const plugins = [
  nodeResolve(),
  commonjs(),
  json(),
  url({
    include: ['**/*.html', '**/*.svg', '**/*.png', '**/*.jp(e)?g', '**/*.gif', '**/*.webp'],
    fileName: '[name][hash][extname]',
    limit: 24000,
  }),
  // Replace literal string 'process.env.NODE_ENV' with the current NODE_ENV
  replace({
    preventAssignment: true,
    values: { 'process.env.NODE_ENV': NODE_ENV },
  }),
  typescript({
    // In watch mode, always emit javascript even with errors (otherwise rollup will terminate)
    noEmitOnError: !ROLLUP_WATCH,
  }),
  svgr(),
  // import from .css, .less, and inject into the document <head></head>
  postCss({
    plugins: [
      autoPrefixer(),
      postCssColorFix({
        colors: colorMap,
      }),
      postCssNested(),
      postCssUrl({
        url: 'inline',
      }),
    ],
  }),
];

if (ROLLUP_MINIFY) {
  plugins.push(
    terser({
      format: {
        comments: function (node, comment) {
          if (comment.type == 'comment2') {
            // Preserve multiline comments containing any of these strings
            return /@preserve|@license|@cc_on|webpackChunkName|webpackIgnore/i.test(comment.value);
          }
        },
      },
    }),
  );
}

if (ROLLUP_STATS) {
  plugins.push(visualizer({ sourcemap: true }));
}

module.exports = {
  plugins,
};
