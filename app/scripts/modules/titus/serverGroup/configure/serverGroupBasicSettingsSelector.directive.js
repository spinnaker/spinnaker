'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.titus.basicSettingsSelector', [
])
  .directive('titusServerGroupBasicSettingsSelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
        application: '=',
        hideClusterNamePreview: '=',
      },
      templateUrl: require('./serverGroupBasicSettingsDirective.html'),
      controller: 'titusServerGroupBasicSettingsSelectorCtrl as basicSettingsCtrl',
    };
  })
  .controller('titusServerGroupBasicSettingsSelectorCtrl', function($scope, $controller, namingService, $uibModalStack, $state) {
    angular.extend(this, $controller('BasicSettingsMixin', {
      $scope: $scope,
      namingService: namingService,
      $uibModalStack: $uibModalStack,
      $state: $state,
    }));

    this.detailPattern = {
      test: function(detail) {
        var pattern = $scope.command.viewState.templatingEnabled ?
        /^([a-zA-Z_0-9._$-{}\\\^]*(\${.+})*)*$/ :
        /^[a-zA-Z_0-9._$-{}\\\^]*$/;

        return isNotExpressionLanguage(detail) ? pattern.test(detail) : true;
      }
    };

    let isNotExpressionLanguage = (field) => {
      return field && field.indexOf('${') < 0;
    };

  });
