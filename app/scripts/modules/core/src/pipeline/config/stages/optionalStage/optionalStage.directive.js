'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.pipeline.stage.optionalStage.directive', [])
  .directive('optionalStage', function() {
    return {
      restrict: 'E',
      scope: {
        stage: '=',
      },
      templateUrl: require('./optionalStage.directive.html'),
      controller: 'OptionalStageCtrl',
      controllerAs: 'optionalStageCtrl',
    };
  })
  .controller('OptionalStageCtrl', ['$scope', function($scope) {
    this.isOptional = function() {
      return $scope.stage && $scope.stage.stageEnabled;
    };

    this.toggleOptional = function() {
      if (this.isOptional()) {
        delete $scope.stage.stageEnabled;
      } else {
        $scope.stage.stageEnabled = {
          type: 'expression',
        };
      }
    };
  }]);
