import rule from './ng-no-require-angularjs';
import ruleTester from '../utils/ruleTester';

ruleTester.run('ng-no-require-angularjs', rule, {
  valid: [
    {
      code: `
        import { module } from 'angular';
        module('foo', []);
      `,
    },
  ],

  invalid: [
    {
      errors: [{ message: "Prefer module('foo', []) to angular.module('foo', [])" }],
      code: `
        import angular from 'angular';
        angular.module('foo', []);
      `,
      output: `
        import { module } from 'angular';
        module('foo', []);
      `,
    },
  ],
});
