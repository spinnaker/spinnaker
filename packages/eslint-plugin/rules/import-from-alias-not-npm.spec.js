'use strict';

const ruleTester = require('../utils/ruleTester');
const rule = require('../rules/import-from-alias-not-npm');

ruleTester.run('import-from-alias-not-npm', rule, {
  valid: [
    {
      filename: '/root/spinnaker/deck/packages/amazon/package/amazon_source_file.ts',
      code: `import { Anything } from '@spinnaker/core';`,
    },
  ],

  invalid: [
    {
      filename: '/root/spinnaker/deck/packages/core/package/core_source_file.ts',
      code: `import { Anything } from '@spinnaker/core';`,
      output: `import { Anything } from 'core';`,
      errors: [
        'Do not use @spinnaker/core to import from core from code inside core.  Instead, use the core alias or a relative import',
      ],
    },
  ],
});
