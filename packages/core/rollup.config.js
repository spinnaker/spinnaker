import fs from 'fs';
import path from 'path';
import copy from 'rollup-plugin-copy';

import baseRollupConfig from '@spinnaker/scripts/config/rollup.config.base.module';
import externalConfigurer from '@spinnaker/scripts/helpers/rollup-node-auto-external-configurer';

const packageJSON = JSON.parse(fs.readFileSync('./package.json', 'utf8'));
const externals = {
  ...packageJSON.dependencies,
  'root/version': true,
};

// In index.ts, the triple slash reference to global namespace types get re-written to match the 'core' alias
// This kludge re-re-writes them back to a relative path
// Likely related to https://github.com/microsoft/TypeScript/issues/36763
const fixTSPathRewrite = () => {
  return {
    name: 'fixTSPathRewrite',
    writeBundle: () => {
      const dts = path.join(__dirname, 'dist', 'index.d.ts');
      if (fs.existsSync(dts)) {
        const fixed = fs
          .readFileSync(dts)
          .toString()
          .replace(/types="types"/, 'types="./types"');
        fs.writeFileSync(dts, fixed);
      }
    },
  };
};

export default {
  ...baseRollupConfig,
  plugins: [
    ...baseRollupConfig.plugins,
    // `core` has some custom type declarations for `promise` and `svg` that needs to be bundled together in the
    // distribution. These custom type declarations are not automatically copied over by typescript, so needs to be
    // explicitly copied here.
    copy({
      targets: [{ src: 'src/types/**/*', dest: 'dist/types' }],
    }),
    fixTSPathRewrite(),
  ],
  external: externalConfigurer(externals),
};
