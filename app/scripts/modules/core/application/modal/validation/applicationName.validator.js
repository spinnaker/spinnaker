'use strict';

let angular = require('angular');

/**
 * Responsible for registering validators around the application name, then running them
 * when creating a new application.
 */
module.exports = angular.module('spinnaker.core.application.applicationName.validator', [
  require('../../../cloudProvider/cloudProvider.registry.js'),
])
  .factory('applicationNameValidator', function(cloudProviderRegistry) {

    const providers = Object.create(null);

    /**
     * Registers a validator for a cloud provider.
     * @param cloudProvider the key of the cloud provider, e.g. "aws", "gce"
     * @param validator the actual validator
     */
    let registerValidator = (cloudProvider, validator) => {
      if (cloudProviderRegistry.getProvider(cloudProvider)) {
        if (!providers[cloudProvider]) {
          providers[cloudProvider] = [];
        }
        providers[cloudProvider].push(validator);
      }
    };

    /**
     * Performs the actual validation. If there are no providers supplied, all configured validators will fire
     * and add their messages to the result.
     * @param applicationName the name of the application
     * @param providersToTest the configured cloud providers; if empty, validators for all providers will fire
     * @returns {{errors: Array, warnings: Array}}
     */
    let validate = (applicationName, providersToTest) => {
      let toCheck = providersToTest && providersToTest.length ?
        providersToTest :
        cloudProviderRegistry.listRegisteredProviders();

      let errors = [],
          warnings = [];

      toCheck.forEach((provider) => {
        if (providers[provider]) {
          providers[provider].forEach((validator) => {
            let results = validator.validate(applicationName);
            results.warnings.forEach((message) => {
              warnings.push({ cloudProvider: provider, message: message});
            });
            results.errors.forEach((message) => {
              errors.push({ cloudProvider: provider, message: message});
            });
          });
        }
      });

      return {
        errors: errors,
        warnings: warnings,
      };
    };

    return {
      registerValidator: registerValidator,
      validate: validate,
    };
  });
