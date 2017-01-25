'use strict';

import {AUTHENTICATION_INITIALIZER_SERVICE} from '../authentication.initializer.service';
import {AUTHENTICATION_SERVICE} from '../authentication.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.authentication.userMenu.directive', [
  require('../../config/settings.js'),
  AUTHENTICATION_INITIALIZER_SERVICE,
  AUTHENTICATION_SERVICE
])
  .directive('userMenu', function(settings, authenticationService, authenticationInitializer) {
    return {
      restrict: 'E',
      replace: true,
      templateUrl: require('./userMenu.directive.html'),
      link: function(scope) {
        scope.authEnabled = settings.authEnabled;
        scope.user = authenticationService.getAuthenticatedUser();
        scope.showLogOutDropdown = () => authenticationService.getAuthenticatedUser().authenticated;
        scope.logOut = () => authenticationInitializer.logOut();
      }
    };
  });
