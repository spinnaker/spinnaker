const checker = require('license-checker');
const path = require('path');

// Regarding the asterisks:
// https://github.com/davglass/license-checker#how-licenses-are-found
const ALLOWED_LICENSES = [
  'Apache*',
  'Apache-2.0',
  'Apache v2.0',
  'BSD',
  'BSD*',
  'BSD-2-Clause',
  'BSD-3-Clause',
  'BSD-3-Clause OR MIT',
  'FreeBSD',
  '(GPL-2.0 OR MIT)',
  'ISC',
  'MIT',
  'MIT*',
  '(MIT AND CC-BY-3.0)',
  'MPL-2.0 OR Apache-2.0',
  '(OFL-1.1 AND MIT)',
  'Unlicense',
  'Zlib',
];

// packages where the license checker makes a bad guess, but the packages themselves have been manually verified to have
// a valid license
const IGNORED = [
  'weak-map@1.0.5', // Apache 2.0 - https://github.com/drses/weak-map
  'colors@0.6.2', // MIT - https://github.com/Marak/colors.js/blob/master/LICENSE
  'gl-mat2@1.0.1', // ZLib - https://github.com/stackgl/gl-mat2
  'gl-mat3@1.0.0', // ZLib - https://github.com/stackgl/gl-mat3
  'gl-vec3@1.0.3', // ZLib - https://github.com/stackgl/gl-vec3
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
        if (!IGNORED.includes(pkg)) {
          console.log(`Package ${pkg} found with license ${properties.licenses}`);
          process.exitCode = 1;
        }
      });
    }
  }
};

checker.init({
  start: path.join(__dirname, '..'),
  production: true, // Only check dependencies, not devDependencies.
}, cb);
