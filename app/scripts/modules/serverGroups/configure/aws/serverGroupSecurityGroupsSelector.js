'use strict';

angular.module('deckApp.serverGroup.configure.aws')
  .directive('awsServerGroupSecurityGroupsSelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
      },
      templateUrl: 'scripts/modules/serverGroups/configure/aws/serverGroupSecurityGroupsDirective.html'
    };
  });
