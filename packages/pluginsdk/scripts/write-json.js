#!/usr/bin/env node
const { writeJsonField, deleteJsonField } = require('./check-plugin/util/readWriteJson');
const yargs = require('yargs').option('delete', { type: 'boolean' });
const [filename, jsonPath, value] = yargs.argv._;

try {
  if (yargs.argv.delete) {
    deleteJsonField(filename, jsonPath);
  } else {
    writeJsonField(filename, jsonPath, value);
  }
} catch (error) {
  console.error(`Unable to write json path ${jsonPath} in file ${filename}`, error);
  process.exit(-1);
}
