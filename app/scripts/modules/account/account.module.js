'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.account', [
  require('exports?"restangular"!imports?_=lodash!restangular'),
  require('../core/cache/caches.module.js'),
  require('./accountTag.directive.js'),
  require('./providerToggles.directive.js'),
  require('./accountLabelColor.directive.js'),
])
.name;
