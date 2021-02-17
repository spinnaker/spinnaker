import nodeResolve from '@rollup/plugin-node-resolve';
import commonjs from '@rollup/plugin-commonjs';
import typescript from '@rollup/plugin-typescript';
import svgr from '@svgr/rollup';
import url from '@rollup/plugin-url';

export default [
  {
    input: 'src/index.ts',
    output: [{ dir: 'dist', format: 'es', sourcemap: true }],
    plugins: [nodeResolve(), commonjs(), typescript(), url(), svgr()],
    external: ['react', 'lodash'],
  },
];
