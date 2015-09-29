'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.preconditions.selector', [
])
  .directive('preconditionSelector', function() {
    return {
      restrict: 'E',
      scope: {
        precondition: '=',
        level: '='
      },
      templateUrl: require('./preconditionSelector.html'),
      controller: 'PreconditionSelectorCtrl',
      controllerAs: 'preconditionCtrl'
    };
  })
  .controller('PreconditionSelectorCtrl', function($scope, preconditionTypeService) {

    $scope.preconditionTypes = preconditionTypeService.listPreconditionTypes();

    if (!$scope.precondition.type && $scope.preconditionTypes && $scope.preconditionTypes.length) {
      $scope.precondition.type = $scope.preconditionTypes[0].key;
    }

    this.clearContext = function () {
      $scope.precondition.context = null;
    };

    this.getPreconditionContextTemplateUrl = function () {
      var preconditionConfig = preconditionTypeService.getPreconditionType($scope.precondition.type);
      return preconditionConfig ? preconditionConfig.contextTemplateUrl : '';
    };

  }).name;
