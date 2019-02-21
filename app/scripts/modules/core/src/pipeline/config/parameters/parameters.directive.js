'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.pipeline.parameters.parameters', [])
  .directive('parameters', function() {
    return {
      restrict: 'E',
      scope: {
        pipeline: '=',
      },
      controller: 'parametersCtrl',
      controllerAs: 'parametersCtrl',
      templateUrl: require('./parameters.html'),
    };
  })
  .controller('parametersCtrl', ['$scope', function($scope) {
    this.addParameter = function() {
      if (!$scope.pipeline.parameterConfig) {
        $scope.pipeline.parameterConfig = [];
      }
      var newParameter = {};
      $scope.pipeline.parameterConfig.push(newParameter);
    };

    this.sortOptions = {
      axis: 'y',
      delay: 150,
      handle: '.glyphicon-resize-vertical',
    };
  }]);
