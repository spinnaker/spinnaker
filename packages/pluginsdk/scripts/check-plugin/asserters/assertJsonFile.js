const { get } = require('lodash');

const assertJsonFile = (report, filename, json) => {
  return function assertJsonFile(field, expectedValue) {
    const currentValue = get(json, field);
    const resolution = {
      description: `Change ${field} in ${filename} from "${currentValue}" to "${expectedValue}"`,
      command: `npx write-json "${filename}" "${field}" "${expectedValue}"`,
    };
    const ok = currentValue === expectedValue;
    report(`Unexpected value in ${filename}: ${field} should be "${expectedValue}"`, ok, resolution);
  };
};

module.exports = { assertJsonFile };
