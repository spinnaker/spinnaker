'use strict';

angular.module('spinnaker.pipelines.parameters')
  .directive('parameter', function() {
    return {
      restrict: 'E',
      scope: {
        parameter: '=',
        pipeline: '='
      },
      controller: 'ParameterCtrl as parameterCtrl',
      templateUrl: 'scripts/modules/pipelines/config/parameters/parameter.html'
    };
  })
  .controller('ParameterCtrl', function($scope) {

    this.remove = function(parameter) {
      var index = $scope.pipeline.parameterConfig.indexOf(parameter);
      $scope.pipeline.parameterConfig.splice(index, 1);
    };

  });
