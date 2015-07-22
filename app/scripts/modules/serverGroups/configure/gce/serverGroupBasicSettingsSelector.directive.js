'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.gce.basicSettingsSelector', [])
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
  .controller('gceServerGroupBasicSettingsSelectorCtrl', function($scope, $controller, RxService, imageService, namingService, $modalStack, $state) {
    angular.extend(this, $controller('BasicSettingsMixin', {
      $scope: $scope,
      RxService: RxService,
      imageService: imageService,
      namingService: namingService,
      $modalStack: $modalStack,
      $state: $state,
    }));
  });
