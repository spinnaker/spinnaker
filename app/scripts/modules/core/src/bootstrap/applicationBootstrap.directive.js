'use strict';

const angular = require('angular');

import {OVERRIDE_REGISTRY} from 'core/overrideRegistry/override.registry';

module.exports = angular.module('spinnaker.applicationBootstrap', [
  require('../navigation/stateactive.directive.js'),
  OVERRIDE_REGISTRY,
])
.directive('spinnaker', function() {
  return {
    restrict: 'E',
    templateUrl: require('./applicationBootstrap.directive.html'),
    controller: function(overrideRegistry) {
      this.spinnakerHeaderTemplate = overrideRegistry.getTemplate('spinnakerHeader', require('./spinnakerHeader.html'));
    },
    controllerAs: 'applicationBootstrapCtrl',
  };
});
