#!/usr/bin/env node
const fs = require('fs');
// JSON parse/stringify with support for comments (tsconfig.json can have comments)
const commentJson = require('comment-json');
const { get, set, unset } = require('lodash');
const yargs = require('yargs');

const configureCli = () => {
  const yargs = require('yargs');

  const addDefaultPositionalArgs = (_yargs) => {
    _yargs.positional('filename', { description: 'The JSON file' });
    _yargs.positional('jsonpath', { description: 'A lodash-style JSON Path' });
  };

  yargs.command(
    'read <filename> <jsonpath>',
    'Outputs a value from a JSON file at the given path',
    addDefaultPositionalArgs,
    ({ filename, jsonpath }) => {
      console.log(readJsonField(filename, jsonpath));
    },
  );

  yargs.command(
    'write <filename> <jsonpath> <value>',
    'Writes a value to a JSON file at the given path',
    (_yargs) => {
      addDefaultPositionalArgs(_yargs);
      _yargs.positional('value', {
        type: 'string',
        description: 'The new value to write',
      });
      _yargs.option('type', {
        description: 'writes the value as a JSON boolean/number/string',
        type: 'string',
        choices: ['boolean', 'number', 'string'],
        default: 'string',
      });
    },
    ({ filename, jsonpath, value, type }) => {
      let newvalue = value;
      if (type === 'boolean') {
        newvalue = newvalue === 'true' ? true : newvalue === 'false' ? false : newvalue;
        if (typeof newvalue !== 'boolean') {
          throw new Error(`Cannot parse ${value} as a boolean`);
        }
      } else if (type === 'number') {
        const newvalue = Number(value);
        if (isNaN(newvalue)) {
          throw new Error(`Cannot parse ${value} as a number`);
        }
      }
      writeJsonField(filename, jsonpath, newvalue);
    },
  );

  yargs.command(
    'delete <filename> <jsonpath>',
    'Deletes a property from a JSON file at the given path',
    addDefaultPositionalArgs,
    ({ filename, jsonpath }) => {
      deleteJsonField(filename, jsonpath);
    },
  );

  yargs.demandCommand(1);
  yargs.strictCommands();
  yargs.wrap(null);

  yargs.example('read-write-json.js read package.json version', 'Outputs the version property from package.json');
  yargs.example(
    'read-write-json.js write package.json devDependencies.husky 6.0.0',
    'Writes "husky": "6.0.0" to the "devDependencies" property of package.json',
  );
  yargs.example(
    'read-write-json.js delete package.json devDependencies.husky',
    'Deletes the "husky" key  from the "devDependencies" property of package.json',
  );
  yargs.parse();
};

if (require.main === module) {
  configureCli();
}

function readJson(filename) {
  const string = fs.readFileSync(filename, 'utf-8');
  return commentJson.parse(string);
}

function writeJson(filename, json) {
  const data = commentJson.stringify(json, null, 2);
  fs.writeFileSync(filename, data, 'utf-8');
}

function readJsonField(filename, path) {
  const json = readJson(filename);
  return get(json, path);
}

function deleteJsonField(filename, field) {
  const json = readJson(filename);
  unset(json, field);
  writeJson(filename, json);
}

function writeJsonField(filename, field, val) {
  const json = readJson(filename);
  set(json, field, val);
  writeJson(filename, json);
}

module.exports.readJson = readJson;
module.exports.writeJson = writeJson;
module.exports.readJsonField = readJsonField;
module.exports.writeJsonField = writeJsonField;
module.exports.deleteJsonField = deleteJsonField;
