'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.azure.serverGroup.configure.azureServerGroupBasicSettingsSelector', [
    require('../../../core/serverGroup/configure/common/basicSettingsMixin.controller.js'),
    require('../../../core/region/regionSelectField.directive.js'),
    require('../../../core/account/accountSelectField.directive.js'),
    require('../../subnet/subnetSelectField.directive.js'),
  ])
  .directive('azureServerGroupBasicSettingsSelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
        application: '=',
        hideClusterNamePreview: '=',
      },
      templateUrl: require('./serverGroupBasicSettingsSelector.directive.html'),
      controller: 'ServerGroupBasicSettingsSelectorCtrl as basicSettingsCtrl',
    };
  })
  .controller('azureServerGroupBasicSettingsSelectorCtrl', function($scope, $controller, imageReader, namingService, $modalStack, $state) {

    angular.extend(this, $controller('BasicSettingsMixin', {
      $scope: $scope,
      imageReader: imageReader,
      namingService: namingService,
      $modalStack: $modalStack,
      $state: $state,
    }));
  });
