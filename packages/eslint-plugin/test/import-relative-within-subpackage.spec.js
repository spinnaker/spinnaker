'use strict';
const mockModule = require('../utils/mockModule');
const mock = mockModule('../utils/import-aliases');
mock.getAllSpinnakerPackages.mockImplementation(() => ['core', 'amazon', 'kubernetes']);

const path = require('path');
const ruleTester = require('../utils/ruleTester');
const rule = require('../rules/import-relative-within-subpackage');

ruleTester.run('import-relative-within-subpackage', rule, {
  valid: [
    {
      filename: '/root/spinnaker/deck/app/scripts/modules/amazon/package/amazon_source_file.ts',
      code: `import { Anything } from '../subpackage/foo';`,
    },
    {
      filename: '/root/spinnaker/deck/app/scripts/modules/amazon/src/package/amazon_source_file.ts',
      code: `import { Anything } from '../subpackage/foo';`,
    },
    {
      filename: '/root/spinnaker/deck/app/scripts/modules/core/subpackage/nest/core_source_file.ts',
      code: `import { Anything } from 'core/subpackage';`,
    },
    {
      // kubernetes subpackages are nested under v1 or v2 directory so treat them differently
      filename:
        '/Users/cthielen/netflix/spinnaker/deck/app/scripts/modules/kubernetes/src/v1/pipeline/stages/runJob/configureJob.controller.js',
      code: `import { Anything } from 'kubernetes/v1/container/lifecycleHook.component';`,
    },
  ],

  invalid: [
    {
      filename: '/root/spinnaker/deck/app/scripts/modules/core/subpackage/core_source_file.ts',
      code: `import { Anything } from 'core/subpackage/foo';`,
      output: `import { Anything } from './foo';`,
      errors: [
        'Do not use an alias to import from core/subpackage from code inside core/subpackage. Instead, use a relative import',
      ],
    },
    {
      filename: '/root/spinnaker/deck/app/scripts/modules/core/subpackage/core_source_file.ts',
      code: `import { Anything } from 'core/subpackage/foo/bar';`,
      output: `import { Anything } from './foo/bar';`,
      errors: [
        'Do not use an alias to import from core/subpackage from code inside core/subpackage. Instead, use a relative import',
      ],
    },
    {
      filename: '/root/spinnaker/deck/app/scripts/modules/core/subpackage/nest/core_source_file.ts',
      code: `import { Anything } from 'core/subpackage/foo/bar';`,
      output: `import { Anything } from '../foo/bar';`,
      errors: [
        'Do not use an alias to import from core/subpackage from code inside core/subpackage. Instead, use a relative import',
      ],
    },
    {
      // kubernetes subpackages are nested under v1 or v2 directory so treat them differently
      filename:
        '/Users/cthielen/netflix/spinnaker/deck/app/scripts/modules/kubernetes/src/v1/pipeline/stages/runJob/configureJob.controller.js',
      code: `import { Anything } from 'kubernetes/v1/pipeline/lifecycleHook.component';`,
      output: `import { Anything } from '../../lifecycleHook.component';`,
      errors: [
        'Do not use an alias to import from kubernetes/v1/pipeline from code inside kubernetes/v1/pipeline. Instead, use a relative import',
      ],
    },
    {
      // kubernetes subpackages are nested under v1 or v2 directory so treat them differently
      filename:
        '/Users/cthielen/netflix/spinnaker/deck/app/scripts/modules/kubernetes/src/v2/manifest/manifestImageDetails.component.ts',
      code: `import { Anything } from 'kubernetes/v2/manifest/ManifestImageDetails';`,
      output: `import { Anything } from './ManifestImageDetails';`,
      errors: [
        'Do not use an alias to import from kubernetes/v2/manifest from code inside kubernetes/v2/manifest. Instead, use a relative import',
      ],
    },
  ],
});
