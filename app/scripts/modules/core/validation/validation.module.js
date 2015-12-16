'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.validation', [
  require('./validateUnique.directive.js'),
  require('./triggerValidation.directive.js'),
  require('./validationError.directive.js')
]);
