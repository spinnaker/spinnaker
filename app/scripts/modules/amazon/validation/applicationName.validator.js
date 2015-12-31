'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.aws.validation.applicationName', [
    require('../../core/application/modal/validation/applicationName.validator.js'),
  ])
  .factory('awsApplicationNameValidator', function () {

    function validateSpecialCharacters(name, errors) {
      let pattern = /^[a-zA-Z_0-9.]*$/g;
      if (!pattern.test(name)) {
        errors.push('Only dot(.) and underscore(_) special characters are allowed.');
      }
    }

    function validateLoadBalancerCharacters(name, warnings) {
      if (name.indexOf('.') > -1 || name.indexOf('_') > -1) {
        warnings.push(`If the application's name contains an underscore(_) or dot(.),
          you will not be able to create a load balancer,
          preventing it from being used as a front end service.`);
      }
    }

    function validateLength(name, warnings, errors) {
      if (name.length > 250) {
        errors.push('The maximum length for an application in Amazon is 250 characters.');
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
    }

    function validate(name) {
      let warnings = [],
          errors = [];

      if (name && name.length) {
        validateSpecialCharacters(name, errors);
        validateLoadBalancerCharacters(name, warnings);
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
  .run(function(applicationNameValidator, awsApplicationNameValidator) {
    applicationNameValidator.registerValidator('aws', awsApplicationNameValidator);
  });
