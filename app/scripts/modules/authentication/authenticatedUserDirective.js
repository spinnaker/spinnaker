'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.authentication.directive', [
  require('./authenticationService.js'),
])
  .directive('authenticatedUser', function(authenticationService) {
    return {
      restrict: 'E',
      template: require('./authenticatedUser.html'),
      link: function(scope) {
        scope.user = authenticationService.getAuthenticatedUser();
      }
    };
  });
