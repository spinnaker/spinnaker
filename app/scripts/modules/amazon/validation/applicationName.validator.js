'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.aws.validation.applicationName', [
    require('../../core/application/modal/validation/applicationName.validator.js'),
  ])
  .factory('awsApplicationNameValidator', function () {

    function validate(name) {
      let warnings = [],
          errors = [];
      name = name || '';
      if (name.indexOf('.') > -1 || name.indexOf('_') > -1) {
        warnings.push(`If the application's name contains an underscore(_) or dot(.),
          you will not be able to create a load balancer,
          preventing it from being used as a front end service.`);
      }

      if (name.length > 20) {
        if (name.length > 32) {
          warnings.push(`You will not be able to create an Amazon load balancer for this application if the
          application's name is longer than 32 characters (currently: ${name.length} characters)`);
        } else {
          if (name.length >= 30) {
            warnings.push(`If you plan to create load balancers for this application, be aware that the character limit
            for load balancer names is 32 (currently: ${name.length} characters). With separators ("-"), you will not
            be able to add a stack and detail field to any load balancer.`);
          } else {
            let remaining = 30 - name.length;
            warnings.push(`If you plan to create load balancers for this application, be aware that the character limit
            for load balancer names is 32. You will only have ~${remaining} characters to add a stack or detail
            field to any load balancer.`);
          }
        }
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
  .run(function(applicationNameValidator, awsApplicationNameValidator) {
    applicationNameValidator.registerValidator('aws', awsApplicationNameValidator);
  });
