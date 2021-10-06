import rule from './import-from-presentation-not-core';
import ruleTester from '../utils/ruleTester';
const errorMessage = (moduleName) => `${moduleName} must be imported from @spinnaker/presentation`;

ruleTester.run('import-from-presentation-not-core', rule, {
  valid: [
    { code: `import { Icon, Illustration } from '@spinnaker/presentation';` },
    { code: `import { LabeledValueList } from '@spinnaker/core';` },
    { code: `import { Application } from '@spinnaker/core';` },
  ],
  invalid: [
    {
      code: `
      import { Icon, NotMigratedModule } from '@spinnaker/core';
      import { Foo } from '@spinnaker/presentation';
      `,
      errors: [errorMessage('Icon')],
      output: `
      import {  NotMigratedModule } from '@spinnaker/core';
      import { Foo, Icon } from '@spinnaker/presentation';
      `,
    },
    {
      code: `
      import { Icon, NotMigratedModule } from '@spinnaker/core';
      `,
      errors: [errorMessage('Icon')],
      output: `
      import {  NotMigratedModule } from '@spinnaker/core';\nimport {Icon} from '@spinnaker/presentation';
      `,
    },
    {
      code: `
      import React from 'react';
      import { Icon } from '@spinnaker/core';
      `,
      errors: [errorMessage('Icon')],
      output: `
      import React from 'react';
      \nimport {Icon} from '@spinnaker/presentation';
      `,
    },
  ],
});
