const angular = require('angular');

import { AUTHENTICATION_INITIALIZER_SERVICE } from '../authentication.initializer.service';
import { AUTHENTICATION_SERVICE } from '../authentication.service';
import { SETTINGS } from 'core/config/settings';

export const AUTHENTICATION_USER_MENU = 'spinnaker.core.authentication.userMenu.directive';

const ngmodule = angular.module(AUTHENTICATION_USER_MENU, [AUTHENTICATION_INITIALIZER_SERVICE, AUTHENTICATION_SERVICE]);

ngmodule.directive('userMenu', function(authenticationService: any, authenticationInitializer: any) {
  return {
    restrict: 'E',
    replace: true,
    templateUrl: require('./userMenu.directive.html'),
    link: function(scope: any) {
      scope.authEnabled = SETTINGS.authEnabled;
      scope.user = authenticationService.getAuthenticatedUser();
      scope.showLogOutDropdown = () => authenticationService.getAuthenticatedUser().authenticated;
      scope.logOut = () => authenticationInitializer.logOut();
    },
  };
});
