'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.fastProperty.selector.directive', [])
  .directive('fastPropertySelector', ($templateCache, $compile) => {
    return {
      restrict: 'E',
      template: '<ng-include src="getTemplateUrl()"/>',
      scope: {
        content: '='
      },
      controller: ($scope) => {
        $scope.clusters = $scope.content.context;
        $scope.getTemplateUrl = () => {
          if($scope.content && $scope.content.selected) {
            return $scope.content.selected.templateUrl;
          }
        };
      }
    };
  })
  .name;
