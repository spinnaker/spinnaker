'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.azure.serverGroup.configure.wizard.capacity.selector.directive', [])
  .directive('azureServerGroupCapacitySelector', function() {
    return {
      restrict: 'E',
      templateUrl: require('./capacitySelector.directive.html'),
      scope: {},
      bindToController: {
        command: '=',
      },
      controllerAs: 'cap',
      controller: 'azureServerGroupCapacitySelectorCtrl',
    };
  })
  .controller('azureServerGroupCapacitySelectorCtrl', function() {});
