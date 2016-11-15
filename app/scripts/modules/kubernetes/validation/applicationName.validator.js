'use strict';

import {APPLICATION_NAME_VALIDATOR} from 'core/application/modal/validation/applicationName.validator';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.kubernetes.validation.applicationName', [
    APPLICATION_NAME_VALIDATOR,
  ])
  .factory('kubernetesApplicationNameValidator', function () {

    function validateSpecialCharacters(name, errors) {
      let pattern = /^([a-zA-Z][a-zA-Z0-9]*)?$/;
      if (!pattern.test(name)) {
        errors.push('The application name must begin with a letter and must contain only letters or digits. No ' +
          'special characters are allowed.');
      }
    }

    function validateLength(name, warnings, errors) {
      // Kubernetes resource names must match [a-z]([-a-z0-9]*[a-z0-9])?
      let maxResourceNameLength = 63;

      let maxServiceNameLength = 24;

      if (name.length > maxResourceNameLength) {
        errors.push('The maximum length for an application in Kubernetes is 63 characters.');
        return;
      }

      if (name.length > maxServiceNameLength) {
        if (name.length > maxServiceNameLength) {
          warnings.push(`You will not be able to create a Kubernetes load balancer for this application if the
            application's name is longer than ${maxServiceNameLength} characters (currently: ${name.length}
          characters).`);
        } else if (name.length >= maxServiceNameLength - 2) {
          warnings.push('With separators ("-"), you will not be able to include a stack and detail field for ' +
            'Kubernetes load balancers.');
        } else {
          let remaining = maxServiceNameLength - 2 - name.length;
          warnings.push(`If you plan to include a stack or detail field for Kubernetes load balancers, you will only
            have ~${remaining} characters to do so.`);
        }
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
  .run(function(applicationNameValidator, kubernetesApplicationNameValidator) {
    applicationNameValidator.registerValidator('kubernetes', kubernetesApplicationNameValidator);
  });
