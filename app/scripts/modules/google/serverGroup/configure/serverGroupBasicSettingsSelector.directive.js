'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.gce.basicSettingsSelector', [
  require('../../../core/serverGroup/configure/common/basicSettingsMixin.controller.js'),
  require('../../gceRegionSelectField.directive.js'),
  require('../../gceZoneSelectField.directive.js'),
  require('../../gceNetworkSelectField.directive.js'),
])
  .directive('gceServerGroupBasicSettingsSelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
        application: '=',
        hideClusterNamePreview: '=',
      },
      templateUrl: require('./serverGroupBasicSettingsDirective.html'),
      controller: 'gceServerGroupBasicSettingsSelectorCtrl as basicSettingsCtrl',
    };
  })
  .controller('gceServerGroupBasicSettingsSelectorCtrl', function($scope, $controller, imageReader, namingService, $uibModalStack, $state) {
    angular.extend(this, $controller('BasicSettingsMixin', {
      $scope: $scope,
      imageReader: imageReader,
      namingService: namingService,
      $uibModalStack: $uibModalStack,
      $state: $state,
    }));
  });
