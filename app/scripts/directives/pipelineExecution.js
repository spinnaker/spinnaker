'use strict';

angular.module('deckApp')
  .directive('pipelineExecution', function() {
    return {
      restrict: 'E',
      replace: true,
      templateUrl: 'views/directives/pipelineexecution.html',
      scope: {
        execution: '=',
        filter: '=',
      },
      controller: 'PipelineExecutionCtrl as ctrl',
    };
  });
