import ruleTester from '../utils/ruleTester';
import rule from './RULENAME';

ruleTester.run('RULENAME', rule, {
  valid: [
    {
      code: "import { Something } from 'somewhere';",
    },
  ],
  invalid: [
    {
      code: "import { API } from '@spinnaker/core';",
      output: "import {  } from '@spinnaker/core';",
      errors: ['Do not import API'],
    },
  ],
});
