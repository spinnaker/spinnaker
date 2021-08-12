// This registers Typescript compiler instance into node.js's require()
const path = require('path');
const project = path.resolve(__dirname, 'tsconfig.json');
require('ts-node').register({ transpileOnly: true, project });

module.exports = require('./eslint-plugin.ts').default;
