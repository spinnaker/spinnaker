'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.serverGroup.configure.aws.awsServerGroupBasicSettingsSelector', [
    require('../../../core/serverGroup/configure/common/basicSettingsMixin.controller.js'),
    require('../../../core/region/regionSelectField.directive.js'),
    require('../../../core/account/accountSelectField.directive.js'),
    require('../../subnet/subnetSelectField.directive.js'),
  ])
  .directive('awsServerGroupBasicSettingsSelector', function() {
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
  .controller('ServerGroupBasicSettingsSelectorCtrl', function($scope, $controller, RxService, imageReader, namingService, $uibModalStack, $state) {

    angular.extend(this, $controller('BasicSettingsMixin', {
      $scope: $scope,
      RxService: RxService,
      imageReader: imageReader,
      namingService: namingService,
      $uibModalStack: $uibModalStack,
      $state: $state,
    }));
  })
  .name;
