'use strict';

const ruleTester = require('../utils/ruleTester');
const rule = require('../rules/import-from-npm-not-relative');

ruleTester.run('import-from-npm-not-relative', rule, {
  valid: [
    {
      filename: '/root/spinnaker/deck/packages/amazon/package/amazon_source_file.ts',
      code: `import { Anything } from '../othersubpackage/file2';`,
    },
  ],

  invalid: [
    {
      filename: '/root/spinnaker/deck/packages/amazon/package/amazon_source_file.ts',
      code: `import { Anything } from '../../core/subpackage/file2';`,
      output: `import { Anything } from '@spinnaker/core';`,
      errors: [
        'Do not use a relative import to import from core from code inside amazon. Instead, use the npm package @spinnaker/core',
      ],
    },
  ],
});
