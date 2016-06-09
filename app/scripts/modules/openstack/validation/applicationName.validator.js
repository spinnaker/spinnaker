'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.openstack.validation.applicationName', [
    require('../../core/application/modal/validation/applicationName.validator.js'),
  ])
  .factory('openstackApplicationNameValidator', function () {

    function validateSpecialCharacters(name, errors) {
      let pattern = /^[a-zA-Z_0-9.]*$/g;
      if (!pattern.test(name)) {
        errors.push('Only dot(.) and underscore(_) special characters are allowed.');
      }
    }

    function validateLength(name, warnings, errors) {
      if (name.length > 250) {
        errors.push('The maximum length for an application in Openstack is 250 characters.');
        return;
      }
      if (name.length > 240) {
        if (name.length >= 248) {
          warnings.push('You will not be able to include a stack or detail field for clusters or security groups.');
        } else {
          let remaining = 248 - name.length;
          warnings.push(`If you plan to include a stack or detail field for clusters, you will only
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
  .run(function(applicationNameValidator, openstackApplicationNameValidator) {
    applicationNameValidator.registerValidator('openstack', openstackApplicationNameValidator);
  });
