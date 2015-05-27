'use strict';

angular.module('spinnaker.pipelines')
  .directive('pipelineConfigNav', function() {
    return {
      restrict: 'E',
      require: '^pipelineConfigurer',
      templateUrl: 'scripts/modules/pipelines/config/nav/pipelineConfigNav.html',
    };
  });
