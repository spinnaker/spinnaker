const { readJson, writeJson } = require('../util/readWriteJson');
const { get, set } = require('lodash');

const writeJsonField = (filename, field, val) => {
  const json = readJson(filename);
  set(json, field, val);
  writeJson(filename, json);
};

const assertJsonFile = (report, filename, json) => {
  return function assertJsonFile(field, expectedValue) {
    const currentValue = get(json, field);
    const resolution = `--fix: change ${field} in ${filename} from "${currentValue}" to "${expectedValue}"`;
    const fixer = () => {
      writeJsonField(filename, field, expectedValue);
      console.log(`fixed: changed ${field} in ${filename} from "${currentValue}" to "${expectedValue}"`);
    };
    report(`${filename}: ${field} field`, currentValue === expectedValue, resolution, fixer);
  };
};

module.exports = { assertJsonFile };
