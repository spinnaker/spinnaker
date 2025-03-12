import commonjs from '@rollup/plugin-commonjs';
import nodeResolve from '@rollup/plugin-node-resolve';
import typescript from '@rollup/plugin-typescript';
import url from '@rollup/plugin-url';
import svgr from '@svgr/rollup';

export default [
  {
    input: 'src/index.ts',
    output: [{ dir: 'dist', format: 'es', sourcemap: true }],
    plugins: [nodeResolve(), commonjs(), typescript(), url(), svgr()],
    external: ['react', 'lodash'],
  },
];
