'use strict';

angular.module('spinnaker.serverGroup.configure.aws')
  .directive('awsServerGroupBasicSettingsSelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
        application: '=',
        hideClusterNamePreview: '=',
      },
      templateUrl: 'scripts/modules/serverGroups/configure/aws/serverGroupBasicSettingsDirective.html',
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
  });
