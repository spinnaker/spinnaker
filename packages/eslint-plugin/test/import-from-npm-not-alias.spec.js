'use strict';
const mockModule = require('../utils/mockModule');
const mock = mockModule('../utils/import-aliases');
mock.getAllSpinnakerPackages.mockImplementation(() => ['core', 'amazon', 'titus', 'docker']);

const ruleTester = require('../utils/ruleTester');
const rule = require('../rules/import-from-npm-not-alias');

ruleTester.run('import-from-npm-not-alias', rule, {
  valid: [
    {
      filename: '/root/spinnaker/deck/packages/amazon/package/amazon_source_file.ts',
      code: `import { Anything } from 'amazon/otherpackage';`,
    },
  ],

  invalid: [
    {
      filename: '/root/spinnaker/deck/packages/amazon/package/amazon_source_file.ts',
      code: `import { Anything } from 'core/otherpackage';`,
      output: `import { Anything } from '@spinnaker/core';`,
      errors: [
        'Do not use an alias to import from core from code inside amazon. Instead, use the npm package @spinnaker/core',
      ],
    },
  ],
});
