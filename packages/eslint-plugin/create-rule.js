#!/usr/bin/env node
const yargs = require('yargs').usage('$0 <rulename>', 'create a new eslint rule', (_yargs) =>
  _yargs.positional('rulename', { description: 'the name of the rule' }),
);

const { existsSync } = require('fs');
const { readFile, writeFile, mkdir } = require('fs').promises;

const prettier = require('prettier');
const glob = require('fast-glob');
const { camelCase } = require('lodash');

const { rulename } = yargs.argv;
const symbol = camelCase(rulename);

const load = async (filename) => readFile(filename, 'utf-8');
const store = async (filename, content) => writeFile(filename, content, 'utf-8');

const prepend = async (filename, insertString) => {
  const content = await load(filename);
  return store(filename, `${insertString}${content}`);
};

const insertAfter = async (filename, insertString, matchString) => {
  const content = await load(filename, 'utf-8');
  const matchIdx = content.indexOf(matchString);
  if (matchIdx === -1) {
    throw new Error(`Did not find '${matchString}' in ${filename}`);
  }
  const prefix = content.substr(0, matchIdx + matchString.length);
  const suffix = content.substr(matchIdx + matchString.length);
  return store(filename, `${prefix}${insertString}${suffix}`);
};

const replaceInFile = async (srcFile, destFile, findPattern, replaceString) => {
  const content = await load(srcFile);
  return store(destFile, content.replace(findPattern, replaceString));
};

const formatFiles = async (globs) => {
  const files = await glob(globs);
  for (const file of files) {
    await formatFile(file);
  }
};

const formatFile = async (filepath) => {
  const fileContent = await load(filepath, 'utf-8');
  const prettierConfig = await prettier.resolveConfig(filepath);
  const options = { ...prettierConfig, filepath };
  return store(filepath, prettier.format(fileContent, options));
};

async function createRule() {
  if (!existsSync('rules')) {
    await mkdir('rules');
  }

  await replaceInFile(`template/template-rule.ts`, `rules/${rulename}.ts`, /RULENAME/g, rulename);
  await replaceInFile(`template/template-rule.spec.ts`, `rules/${rulename}.spec.ts`, /RULENAME/g, rulename);
  await prepend('eslint-plugin.ts', `import ${camelCase(rulename)} from './rules/${rulename}';\n`);
  await insertAfter('eslint-plugin.ts', `\n    '${rulename}': ${camelCase(rulename)},`, 'rules: {');
  await insertAfter('base.config.js', `\n    '@spinnaker/${rulename}': 2,`, 'rules: {');
  await formatFiles([`rules/${rulename}.**`, 'eslint-plugin.js', 'base.config.js']);
}

createRule();
