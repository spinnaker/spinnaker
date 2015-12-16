'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.account', [
  require('./accountTag.directive.js'),
  require('./providerToggles.directive.js'),
  require('./accountLabelColor.directive.js'),
]);
