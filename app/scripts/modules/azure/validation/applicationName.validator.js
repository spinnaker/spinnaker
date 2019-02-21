'use strict';

const angular = require('angular');

import { ApplicationNameValidator } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.azure.validation.applicationName', [])
  .factory('azureApplicationNameValidator', function() {
    function validateSpecialCharacters(name, errors) {
      const pattern = /^([a-zA-Z][a-zA-Z0-9]*)?$/;
      if (!pattern.test(name)) {
        errors.push(
          'The application name must begin with a letter and must contain only letters or digits. No ' +
            'special characters are allowed.',
        );
      }
    }

    function validate(name) {
      const warnings = [],
        errors = [];

      if (name && name.length) {
        validateSpecialCharacters(name, errors);
      }

      return {
        warnings: warnings,
        errors: errors,
      };
    }

    return {
      validate: validate,
    };
  })
  .run([
    'azureApplicationNameValidator',
    function(azureApplicationNameValidator) {
      ApplicationNameValidator.registerValidator('azure', azureApplicationNameValidator);
    },
  ]);
