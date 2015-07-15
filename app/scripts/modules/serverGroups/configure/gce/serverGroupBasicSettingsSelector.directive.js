'use strict';

angular.module('spinnaker.serverGroup.configure.gce')
  .directive('gceServerGroupBasicSettingsSelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
        application: '=',
        hideClusterNamePreview: '=',
      },
      templateUrl: 'scripts/modules/serverGroups/configure/gce/serverGroupBasicSettingsDirective.html',
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
