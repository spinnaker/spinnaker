const checker = require('license-checker');
const path = require('path');

// Regarding the asterisks:
// https://github.com/davglass/license-checker#how-licenses-are-found
const ALLOWED_LICENSES = [
  'MIT',
  'MIT*',
  'BSD',
  'Apache-2.0',
  'Apache*',
  'BSD-3-Clause OR MIT',
  '(GPL-2.0 OR MIT)',
  'BSD-3-Clause',
  'BSD-2-Clause',
  'MPL-2.0 OR Apache-2.0',
  'Apache v2.0',
];

const cb = (err, licenses) => {
  if (err) {
    console.log('Error: ', err);
    process.exitCode = 1;
  } else {
    const unknownLicenses = Object.entries(licenses)
      .filter(([pkg, properties]) => !ALLOWED_LICENSES.includes(properties.licenses));
    if (unknownLicenses.length) {
      unknownLicenses.forEach(([pkg, properties]) => {
        console.log(`Package ${pkg} found with license ${properties.licenses}`);
      });
      process.exitCode = 1;
    }
  }
};

checker.init({
  start: path.join(__dirname, '..'),
  production: true, // Only check dependencies, not devDependencies.
}, cb);
