'use strict';

angular.module('spinnaker.pipelines.config.validator.directive', ['spinnaker.pipelines.config.validator.service'])
  .directive('pipelineConfigErrors', function() {
    return {
      restrict: 'E',
      templateUrl: 'scripts/modules/pipelines/config/validation/pipelineConfigErrors.html',
      scope: {
        pipeline: '='
      },
      controller: 'PipelineConfigValidatorCtrl',
      controllerAs: 'validatorCtrl',
    };
  })
  .controller('PipelineConfigValidatorCtrl', function($scope, pipelineConfigValidator) {

    function validate() {
      $scope.messages = pipelineConfigValidator.validatePipeline($scope.pipeline);
    }

    $scope.$watch('pipeline', validate, true);

    $scope.popover = { show: false };

  });
