'use strict';

angular.module('deckApp.serverGroup.configure.gce')
  .directive('gceServerGroupCapacitySelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
      },
      templateUrl: 'scripts/modules/serverGroups/configure/gce/serverGroupCapacityDirective.html',
    };
  });
