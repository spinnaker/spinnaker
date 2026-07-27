import fs from 'fs';
import path from 'path';

import baseRollupConfig from '@spinnaker/scripts/config/rollup.config.base.module';
import externalConfigurer from '@spinnaker/scripts/helpers/rollup-node-auto-external-configurer';

const packageJSON = JSON.parse(fs.readFileSync('./package.json', 'utf8'));
const externals = {
  ...packageJSON.dependencies,
  ...packageJSON.peerDependencies,
};

// @rollup/plugin-node-resolve v15 defaults to ['.mjs', '.js', '.json', '.node'] with no .ts/.tsx.
// This plugin fills the gap: when a bare relative directory import can't be resolved, try
// appending /index.ts and /index.tsx so that TypeScript barrel files are found.
const tsDirResolvePlugin = {
  name: 'ts-dir-resolve',
  resolveId(source, importer) {
    if (!importer || !source.startsWith('.')) return null;
    const dir = path.resolve(path.dirname(importer), source);
    for (const candidate of [`${dir}/index.ts`, `${dir}/index.tsx`, `${dir}.ts`, `${dir}.tsx`]) {
      if (fs.existsSync(candidate)) return candidate;
    }
    return null;
  },
};

export default {
  ...baseRollupConfig,
  plugins: [tsDirResolvePlugin, ...baseRollupConfig.plugins],
  external: externalConfigurer(externals),
};
