const { get } = require('lodash');

const assertJsonFile = (report, filename, json) => {
  return function assertJsonFile(field, expectedValue) {
    const currentValue = get(json, field);

    const deleteResolution = {
      description: `Delete ${field} in ${filename}`,
      command: `npx write-json --delete "${filename}" "${field}"`,
    };

    const changeRresolution = {
      description: `Change ${field} in ${filename} from "${currentValue}" to "${expectedValue}"`,
      command: `npx write-json "${filename}" "${field}" "${expectedValue}"`,
    };

    const resolution = expectedValue === undefined ? deleteResolution : changeRresolution;
    const ok = currentValue === expectedValue;
    report(`Unexpected value in ${filename}: ${field} should be "${expectedValue}"`, ok, resolution);
  };
};

module.exports = { assertJsonFile };
