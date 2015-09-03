'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.validation', [
  require('./validateUnique.js'),
  require('./triggerValidation.js'),
  require('./validationError.js')
])
.name;
