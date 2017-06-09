'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.google.serverGroup.configure.wizard.capacity.zone.directive', [
  ])
  .directive('gceZoneSelector', function() {
    return {
      restrict: 'E',
      templateUrl: require('./zoneSelector.directive.html'),
      scope: {},
      bindToController: {
        command: '=',
      },
      controllerAs: 'vm',
      controller: angular.noop,
    };
  });
