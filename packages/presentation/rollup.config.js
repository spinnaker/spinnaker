import nodeResolve from '@rollup/plugin-node-resolve';
import commonjs from '@rollup/plugin-commonjs';
import typescript from '@rollup/plugin-typescript';

export default [
  {
    input: 'src/index.ts',
    output: [{ dir: 'dist', format: 'es', sourcemap: true }],
    plugins: [nodeResolve(), commonjs(), typescript()],
    external: ['react', 'lodash'],
  },
];
