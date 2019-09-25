'use strict';

const angular = require('angular');

import { ApplicationNameValidator } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.kubernetes.validation.applicationName', [])
  .factory('kubernetesApplicationNameValidator', function() {
    function validateSpecialCharacters(name, warnings, errors) {
      const alphanumPattern = /^([a-zA-Z][a-zA-Z0-9]*)?$/;
      if (!alphanumPattern.test(name)) {
        const alphanumWithDashPattern = /^([a-zA-Z][a-zA-Z0-9-]*)?$/;
        if (alphanumWithDashPattern.test(name)) {
          warnings.push('Dashes should only be used in application names when using the Kubernetes v2 provider.');
        } else {
          errors.push(
            'The application name must begin with a letter and must contain only letters or digits. ' +
              'No special characters are allowed.',
          );
        }
      }
    }

    function validateLength(name, warnings, errors) {
      // general k8s resource restriction: 253 characters - https://kubernetes.io/docs/concepts/overview/working-with-objects/names/#names
      // safe bet is 63 characters - (see also RFC 1035 or RFC 1123 - both setting DNS label maximum to 63 chars)
      // for service names: - https://github.com/kubernetes/kubernetes/pull/29523 (until K8s 1.4.0 it was 24 characters https://github.com/kubernetes/kubernetes/issues/12463)
      // or annotations:    - https://kubernetes.io/docs/concepts/overview/working-with-objects/annotations/#syntax-and-character-set

      const maxResourceNameLength = 63;

      if (name.length > maxResourceNameLength) {
        errors.push('The maximum length for an application in Kubernetes is ${maxResourceNameLength} characters.');
        return;
      }
    }

    function validate(name) {
      let warnings = [],
        errors = [];

      if (name && name.length) {
        validateSpecialCharacters(name, warnings, errors);
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
    'kubernetesApplicationNameValidator',
    function(kubernetesApplicationNameValidator) {
      ApplicationNameValidator.registerValidator('kubernetes', kubernetesApplicationNameValidator);
    },
  ]);
