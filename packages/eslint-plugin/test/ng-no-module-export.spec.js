'use strict';

const ruleTester = require('../utils/ruleTester');
const rule = require('../rules/ng-no-module-export');

ruleTester.run('ng-no-module-export', rule, {
  valid: [
    {
      code: `
        const angular = require('angular');
        export const MODULE_NAME = 'foo';
        angular.module(MODULE_NAME, [])
          .component('componentName', {});
      `,
    },
  ],

  invalid: [
    {
      errors: [{ message: 'Prefer exporting the AngularJS module name instead of the entire module' }],
      code: `
        const angular = require('angular');
        module.exports = angular.module('foo', [])
          .component('componentName', {});
      `,
    },
  ],
});
