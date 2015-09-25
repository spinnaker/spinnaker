'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.gce.basicSettingsSelector', [
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
  .controller('gceServerGroupBasicSettingsSelectorCtrl', function($scope, $controller, RxService, imageReader, namingService, $modalStack, $state) {
    angular.extend(this, $controller('BasicSettingsMixin', {
      $scope: $scope,
      RxService: RxService,
      imageReader: imageReader,
      namingService: namingService,
      $modalStack: $modalStack,
      $state: $state,
    }));
  })
.name;
