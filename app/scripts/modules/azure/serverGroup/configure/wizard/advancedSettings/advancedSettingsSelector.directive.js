'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.azure.serverGroup.configure.wizard.advancedSettings.selector.directive', [])
  .directive('azureServerGroupAdvancedSettingsSelector', function() {
    return {
      restrict: 'E',
      templateUrl: require('./advancedSettingsSelector.directive.html'),
      scope: {},
      bindToController: {
        command: '=',
      },
      controllerAs: 'adv',
      controller: 'azureServerGroupAdvancedSettingsSelectorCtrl',
    };
  })
  .controller('azureServerGroupAdvancedSettingsSelectorCtrl', function() {});
