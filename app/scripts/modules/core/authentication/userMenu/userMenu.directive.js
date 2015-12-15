'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.authentication.userMenu.directive', [
  require('../../config/settings.js'),
  require('../authentication.service.js'),
])
  .directive('userMenu', function(settings, authenticationService) {
    return {
      restrict: 'E',
      replace: true,
      templateUrl: require('./userMenu.directive.html'),
      link: function(scope) {
        scope.authEnabled = settings.authEnabled;
        scope.user = authenticationService.getAuthenticatedUser();
      }
    };
  }).name;
