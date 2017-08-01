'use strict';

import { APPLICATION_NAME_VALIDATOR } from '@spinnaker/core';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.dcos.validation.applicationName', [
    APPLICATION_NAME_VALIDATOR,
  ])
  .factory('dcosApplicationNameValidator', function () {

    function validateSpecialCharacters(name, errors) {
      let pattern = /^[a-z0-9]+$/;
      if (!pattern.test(name)) {
        errors.push('The application name can only contain lowercase letters and digits. No other ' +
          'special characters are allowed.');
      }
    }

    function validateLength(name, warnings, errors) {
      let maxResourceNameLength = 127;

      if (name.length > maxResourceNameLength) {
        //TODO Copy pasted from Kubernetes, bumped up to 127 characters from 63, but not sure on the actual limit imposed by DCOS.
        errors.push('The maximum length for an application in DCOS is 127 characters.');
        return;
      }
    }

    function validate(name) {
      let warnings = [],
          errors = [];

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
      validate: validate
    };
  })
  .run(function(applicationNameValidator, dcosApplicationNameValidator) {
    applicationNameValidator.registerValidator('dcos', dcosApplicationNameValidator);
  });
