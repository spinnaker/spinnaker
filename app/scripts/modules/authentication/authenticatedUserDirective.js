'use strict';

angular.module('deckApp.authentication')
  .directive('authenticatedUser', function(authenticationService) {
    return {
      restrict: 'E',
      templateUrl: 'scripts/modules/authentication/authenticatedUser.html',
      link: function(scope) {
        scope.user = authenticationService.getAuthenticatedUser();
      }
    };
  });
