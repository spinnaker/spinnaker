#!/usr/bin/env node

/* eslint-disable no-console */

const FD = { STDIN: 0, STDOUT: 1 };
const _ = require('lodash');
const fs = require('fs');

const yargs = require('yargs')
  .usage('$0 [--to-peerdeps|--from-peerdeps] [--input <input.package.json>] [--output output.package.json]')
  .option('to-peerdeps', {
    boolean: true,
    description: 'Convert a normal package.json to a pluginsdk-peerdeps package.json',
  })
  .option('from-peerdeps', {
    boolean: true,
    description: 'Convert a pluginsdk-peerdepsnormal package.json to a normal  package.json',
  })
  .option('input', {
    description: 'The input package.json (STDIN if omitted)',
    default: FD.STDIN,
  })
  .option('output', {
    description: 'The output package.json (STDOUT if omitted)',
    default: FD.STDOUT,
  })
  .example('$0 --to-peerdeps < ./package.json > package.peerdeps.json')
  .example('$0 --to-peerdeps --input ./package.json --output package.peerdeps.json')
  .example('$0 --from-peerdeps --input package.peerdeps.json > package.json')
  .check((argv) => {
    if (!argv['to-peerdeps'] && !argv['from-peerdeps']) {
      throw new Error('Specify either to-peerdeps or from-peerdeps');
    }

    return true;
  })
  .conflicts('to-peerdeps', 'from-peerdeps');

const { argv } = yargs;
const { input, output } = argv;
const inputString = fs.readFileSync(input, { encoding: 'utf8' });

const packageJson = JSON.parse(inputString);
const depTypes = ['dependencies', 'devDependencies', 'peerDependencies', 'peerDependenciesMeta'];
const sourceDeps = Object.fromEntries(depTypes.map((depType) => [depType, _.cloneDeep(packageJson[depType] || {})]));
depTypes.forEach((depType) => delete packageJson[depType]);

const sortKeys = (obj) => _(obj).toPairs().sortBy(0).fromPairs().value();

if (argv['to-peerdeps']) {
  packageJson.peerDependencies = sortKeys({ ...sourceDeps.dependencies, ...sourceDeps.devDependencies });
  packageJson.peerDependenciesMeta = _(sourceDeps.devDependencies)
    .toPairs()
    .map(([pkg, _version]) => [pkg, { dev: true }])
    .fromPairs()
    .value();

  fs.writeFileSync(output, JSON.stringify(packageJson, null, 2).replace(/{\s*"dev": true\s*}/g, `{ "dev": true }`));
} else if (argv['from-peerdeps']) {
  const devDependencies = Object.entries(sourceDeps.peerDependenciesMeta)
    .filter(([pkg, meta]) => meta && meta.dev)
    .map(([pkg, _meta]) => pkg);

  const packages = Object.entries(sourceDeps.peerDependencies);
  const [devDeps, deps] = _.partition(packages, ([dep, _ver]) => devDependencies.includes(dep));
  packageJson.devDependencies = Object.fromEntries(devDeps);
  packageJson.dependencies = Object.fromEntries(deps);

  fs.writeFileSync(output, JSON.stringify(packageJson, null, 2));
}
