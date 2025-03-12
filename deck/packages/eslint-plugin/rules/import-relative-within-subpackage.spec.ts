/* eslint-disable @spinnaker/import-sort */
import '../utils/import-aliases.mock';
import rule from './import-relative-within-subpackage';
import ruleTester from '../utils/ruleTester';

ruleTester.run('import-relative-within-subpackage', rule, {
  valid: [
    {
      filename: '/root/spinnaker/deck/packages/amazon/package/amazon_source_file.ts',
      code: `import { Anything } from '../subpackage/foo';`,
    },
    {
      filename: '/root/spinnaker/deck/packages/amazon/src/package/amazon_source_file.ts',
      code: `import { Anything } from '../subpackage/foo';`,
    },
  ],

  invalid: [
    {
      filename: '/root/spinnaker/deck/packages/core/subpackage/core_source_file.ts',
      code: `import { Anything } from 'core/subpackage/foo';`,
      output: `import { Anything } from './foo';`,
      errors: [
        'Do not use an alias to import from core/subpackage from code inside core/subpackage. Instead, use a relative import',
      ],
    },
    {
      filename: '/root/spinnaker/deck/packages/core/subpackage/core_source_file.ts',
      code: `import { Anything } from 'core/subpackage/foo/bar';`,
      output: `import { Anything } from './foo/bar';`,
      errors: [
        'Do not use an alias to import from core/subpackage from code inside core/subpackage. Instead, use a relative import',
      ],
    },
    {
      filename: '/root/spinnaker/deck/packages/core/subpackage/nest/core_source_file.ts',
      code: `import { Anything } from 'core/subpackage/foo/bar';`,
      output: `import { Anything } from '../foo/bar';`,
      errors: [
        'Do not use an alias to import from core/subpackage from code inside core/subpackage. Instead, use a relative import',
      ],
    },
    {
      filename: '/root/spinnaker/deck/packages/core/subpackage/nest/core_source_file.ts',
      code: `import { Anything } from 'core/subpackage';`,
      errors: [
        'Do not use an alias to import from core/subpackage from code inside core/subpackage. Instead, use a relative import',
      ],
    },
  ],
});
