#!/usr/bin/env node
const yargs = require('yargs');

const filename = yargs.argv._[0];
const jsonPath = yargs.argv._[1];
const value = yargs.argv._[2];

const { readJson, writeJson } = require('./check-plugin/util/readWriteJson');
const { set } = require('lodash');

const writeJsonField = (filename, field, val) => {
  const json = readJson(filename);
  set(json, field, val);
  writeJson(filename, json);
};

try {
  writeJsonField(filename, jsonPath, value);
} catch (error) {
  console.error(`Unable to write json path ${jsonPath} in file ${filename}`, error);
  process.exit(-1);
}
