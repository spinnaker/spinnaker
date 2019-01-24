'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.validation', [
  require('./validateUnique.directive').name,
  require('./triggerValidation.directive').name,
  require('./validationError.directive').name,
]);
