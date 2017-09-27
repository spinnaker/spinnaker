'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.validation', [
  require('./validateUnique.directive.js').name,
  require('./triggerValidation.directive.js').name,
  require('./validationError.directive.js').name,
]);
