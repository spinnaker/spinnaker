'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.applicationBootstrap', [
  require('../navigation/stateactive.directive.js'),
])
.directive('spinnaker', function() {
  return {
    restrict: 'E',
    templateUrl: require('./applicationBootstrap.directive.html'),
    controller: function(templateOverrideRegistry) {
      this.spinnakerHeaderTemplate = templateOverrideRegistry.getTemplate('spinnakerHeader', require('./spinnakerHeader.html'));
    },
    controllerAs: 'applicationBootstrapCtrl',
  };
})
.name;
