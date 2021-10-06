import rule from './RULENAME';
import ruleTester from '../utils/ruleTester';

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
