'use strict';

angular.module('deckApp')
  .directive('buildTimeline', function() {
    return {
      restrict: 'E',
      replace: true,
      scope: {
        executions: '=',
      },
      templateUrl: 'views/delivery/buildtimeline.html',
      controller: 'BuildTimelineCtrl as ctrl',
    };
  });

