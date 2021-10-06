/* eslint-disable @spinnaker/import-sort */
import '../utils/import-aliases.mock';
import rule from './import-from-npm-not-alias';
import ruleTester from '../utils/ruleTester';

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
