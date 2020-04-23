const { readJson, writeJson } = require('../util/readWriteJson');

const get = (path, obj) => {
  return path.split('.').reduce((acc, key) => (acc !== undefined ? acc[key] : undefined), obj);
};

const writeJsonField = (filename, field, val) => {
  const json = readJson(filename);
  const segments = field.split('.');
  const tail = segments.pop();
  const parent = get(segments.join('.'), json);
  parent[tail] = val;
  writeJson(filename, json);
};

const assertJsonFile = (report, filename, json) => {
  return function assertJsonFile(field, expectedValue) {
    const currentValue = get(field, json);
    const resolution = `--fix: change ${field} in ${filename} from "${currentValue}" to "${expectedValue}"`;
    const fixer = () => {
      writeJsonField(filename, field, expectedValue);
      console.log(`fixed: changed ${field} in ${filename} from "${currentValue}" to "${expectedValue}"`);
    };
    report(`${filename}: ${field} field`, currentValue === expectedValue, resolution, fixer);
  };
};

module.exports = { assertJsonFile };
