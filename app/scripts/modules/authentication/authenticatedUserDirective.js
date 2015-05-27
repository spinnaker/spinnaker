'use strict';

angular.module('spinnaker.authentication.directive', [
  'spinnaker.authentication.service'
])
  .directive('authenticatedUser', function(authenticationService) {
    return {
      restrict: 'E',
      templateUrl: 'scripts/modules/authentication/authenticatedUser.html',
      link: function(scope) {
        scope.user = authenticationService.getAuthenticatedUser();
      }
    };
  });
