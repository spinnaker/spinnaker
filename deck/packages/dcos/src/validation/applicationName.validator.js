'use strict';

import { module } from 'angular';

import { ApplicationNameValidator } from '@spinnaker/core';

export const DCOS_VALIDATION_APPLICATIONNAME_VALIDATOR = 'spinnaker.dcos.validation.applicationName';
export const name = DCOS_VALIDATION_APPLICATIONNAME_VALIDATOR; // for backwards compatibility
module(DCOS_VALIDATION_APPLICATIONNAME_VALIDATOR, [])
  .factory('dcosApplicationNameValidator', function () {
    function validateSpecialCharacters(name, errors) {
      const pattern = /^[a-z0-9]+$/;
      if (!pattern.test(name)) {
        errors.push(
          'The application name can only contain lowercase letters and digits. No other ' +
            'special characters are allowed.',
        );
      }
    }

    function validateLength(name, warnings, errors) {
      const maxResourceNameLength = 127;

      if (name.length > maxResourceNameLength) {
        //TODO Copy pasted from Kubernetes, bumped up to 127 characters from 63, but not sure on the actual limit imposed by DCOS.
        errors.push('The maximum length for an application in DCOS is 127 characters.');
        return;
      }
    }

    function validate(name) {
      const warnings = [];
      const errors = [];

      if (name && name.length) {
        validateSpecialCharacters(name, errors);
        validateLength(name, warnings, errors);
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
    'dcosApplicationNameValidator',
    function (dcosApplicationNameValidator) {
      ApplicationNameValidator.registerValidator('dcos', dcosApplicationNameValidator);
    },
  ]);
