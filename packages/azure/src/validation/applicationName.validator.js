'use strict';

import { module } from 'angular';

import { ApplicationNameValidator } from '@spinnaker/core';

export const AZURE_VALIDATION_APPLICATIONNAME_VALIDATOR = 'spinnaker.azure.validation.applicationName';
export const name = AZURE_VALIDATION_APPLICATIONNAME_VALIDATOR; // for backwards compatibility
module(AZURE_VALIDATION_APPLICATIONNAME_VALIDATOR, [])
  .factory('azureApplicationNameValidator', function () {
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
      const warnings = [];
      const errors = [];

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
    function (azureApplicationNameValidator) {
      ApplicationNameValidator.registerValidator('azure', azureApplicationNameValidator);
    },
  ]);
