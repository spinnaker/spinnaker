'use strict';

angular.module('deckApp.serverGroup.display.tasks.tag', [])
  .directive('runningTasksTag', function() {
    return {
      restrict: 'E',
      scope: {
        application: '=',
        tasks: '='
      },
      templateUrl: 'scripts/modules/serverGroups/pod/runningTasksTag.html',
      link: function(scope) {
        scope.popover = { show: false };
      }
    };
  });
