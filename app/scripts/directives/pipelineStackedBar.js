'use strict';

angular.module('deckApp')
  .directive('pipelineStackedBar', function() {
    return {
      restrict: 'E',
      replace: true,
      scope: {
        executions: '=',
      },
      templateUrl: 'views/delivery/pipelinestackedbar.html',
      controller: 'PipelineStackedBarCtrl as ctrl',
    };

  });
