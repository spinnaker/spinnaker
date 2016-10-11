'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.application.modal.validation.example.applicationName', [
    require('./applicationName.validator.js'),
    require('../../../cloudProvider/cloudProvider.registry.js'),
    require('core/config/settings.js'),
  ])
  .factory('exampleApplicationNameValidator', function () {

    const warningMessage = 'WARNING!!!!',
          warningCondition = 'application.warning',
          errorMessage = 'ERRORRRRRR!!!',
          errorCondition = 'application.error',
          commonWarningCondition = 'common.warning',
          commonWarningMessage = '2COMMON WARNING',
          commonErrorCondition = 'common.error',
          commonErrorMessage = 'COMMON ERROR!';

    function validate(name) {
      let warnings = [],
          errors = [];
      name = name || '';
      if (name === warningCondition) {
        warnings.push(warningMessage);
      }
      if (name === errorCondition) {
        errors.push(errorMessage);
      }
      if (name === commonWarningCondition) {
        warnings.push(commonWarningMessage);
      }
      if (name === commonErrorCondition) {
        errors.push(commonErrorMessage);
      }

      return {
        warnings: warnings,
        errors: errors,
      };
    }

    return {
      provider: 'example',
      validate: validate,
      WARNING_MESSAGE: warningMessage,
      WARNING_CONDITION: warningCondition,
      ERROR_MESSAGE: errorMessage,
      ERROR_CONDITION: errorCondition,
      COMMON_WARNING_MESSAGE: commonWarningMessage,
      COMMON_WARNING_CONDITION: commonWarningCondition,
      COMMON_ERROR_MESSAGE: commonErrorMessage,
      COMMON_ERROR_CONDITION: commonErrorCondition,
    };
  })
  .factory('exampleApplicationNameValidator2', function () {

    const warningMessage = '2WARNING!!!!',
          warningCondition = 'application.warning2',
          errorMessage = '2ERRORRRRRR!!!',
          errorCondition = 'application.error2',
          commonWarningCondition = 'common.warning',
          commonWarningMessage = '2COMMON WARNING',
          commonErrorCondition = 'common.error',
          commonErrorMessage = '2COMMON ERROR!';

    function validate(name) {
      let warnings = [],
          errors = [];
      name = name || '';

      if (name === warningCondition) {
        warnings.push(warningMessage);
      }
      if (name === commonWarningCondition) {
        warnings.push(commonWarningMessage);
      }
      if (name === errorCondition) {
        errors.push(errorMessage);
      }
      if (name === commonErrorCondition) {
        errors.push(commonErrorMessage);
      }

      return {
        warnings: warnings,
        errors: errors,
      };
    }

    return {
      provider: 'example2',
      validate: validate,
      WARNING_MESSAGE: warningMessage,
      WARNING_CONDITION: warningCondition,
      ERROR_MESSAGE: errorMessage,
      ERROR_CONDITION: errorCondition,
      COMMON_WARNING_MESSAGE: commonWarningMessage,
      COMMON_WARNING_CONDITION: commonWarningCondition,
      COMMON_ERROR_MESSAGE: commonErrorMessage,
      COMMON_ERROR_CONDITION: commonErrorCondition,

    };
  })
  .run(function(applicationNameValidator, exampleApplicationNameValidator, exampleApplicationNameValidator2) {
    applicationNameValidator.registerValidator('example', exampleApplicationNameValidator);
    applicationNameValidator.registerValidator('example2', exampleApplicationNameValidator2);
  })
  .config(function(cloudProviderRegistryProvider, settings) {
    settings.providers.example = {};
    settings.providers.example2 = {};
    cloudProviderRegistryProvider.registerProvider('example', {});
    cloudProviderRegistryProvider.registerProvider('example2', {});
  });
