import rule from './ng-no-require-module-deps';
import ruleTester from '../utils/ruleTester';

ruleTester.run('ng-no-require-module-deps', rule, {
  valid: [
    {
      code: `
        import { module } from 'angular';
        import FOO_MODULE from './foo';
        module('foo', [FOO_MODULE]);
      `,
    },
  ],

  invalid: [
    {
      errors: [
        {
          message: 'Prefer \'import ANGULARJS_LIBRARY from "angularjs-library"\' over \'require("angularjs-library")\'',
        },
      ],
      code: `
        import angular from 'angular';
        angular.module('foo', [ require('./foo') ]);
      `,
      output: `
        import angular from 'angular';
import __FOO from './foo';
        angular.module('foo', [ __FOO ]);
      `,
    },
  ],
});
