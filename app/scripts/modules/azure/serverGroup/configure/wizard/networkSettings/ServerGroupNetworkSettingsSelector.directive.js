'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.azure.serverGroup.configure.networkSettings.directive', [])
  .directive('azureServerGroupNetworkSettingsSelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
      },
      templateUrl: require('./ServerGroupNetworkSettingsSelector.directive.html'),
    };
  });
