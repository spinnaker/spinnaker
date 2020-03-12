import nodeResolve from '@rollup/plugin-node-resolve';
import commonjs from '@rollup/plugin-commonjs';
import typescript from '@rollup/plugin-typescript';

export default [
  {
    input: 'src/index.ts',
    plugins: [nodeResolve(), commonjs(), typescript()],
    external: ['@spinnaker/core'],
    output: [{ dir: 'dist', format: 'es', sourcemap: true }],
  },
];
