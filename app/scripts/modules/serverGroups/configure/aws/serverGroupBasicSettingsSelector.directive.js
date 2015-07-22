'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.serverGroup.configure.aws.awsServerGroupBasicSettingsSelector', [
    require('../common/basicSettingsMixin.controller.js'),
  ])
  .directive('awsServerGroupBasicSettingsSelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
        application: '=',
        hideClusterNamePreview: '=',
      },
      templateUrl: require('./serverGroupBasicSettingsDirective.html'),
      controller: 'ServerGroupBasicSettingsSelectorCtrl as basicSettingsCtrl',
    };
  })
  .controller('ServerGroupBasicSettingsSelectorCtrl', function($scope, $controller, RxService, imageService, namingService, $modalStack, $state) {

    angular.extend(this, $controller('BasicSettingsMixin', {
      $scope: $scope,
      RxService: RxService,
      imageService: imageService,
      namingService: namingService,
      $modalStack: $modalStack,
      $state: $state,
    }));
  })
  .name;
