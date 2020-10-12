#!/usr/bin/env node
const { writeJsonField } = require('./check-plugin/util/readWriteJson');
const [filename, jsonPath, value] = require('yargs').argv._;

try {
  writeJsonField(filename, jsonPath, value);
} catch (error) {
  console.error(`Unable to write json path ${jsonPath} in file ${filename}`, error);
  process.exit(-1);
}
