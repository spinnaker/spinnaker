const angular = require('angular');

import { AUTHENTICATION_INITIALIZER_SERVICE } from '../authentication.initializer.service';
import { AuthenticationService } from '../AuthenticationService';
import { SETTINGS } from 'core/config/settings';

export const AUTHENTICATION_USER_MENU = 'spinnaker.core.authentication.userMenu.directive';

const ngmodule = angular.module(AUTHENTICATION_USER_MENU, [AUTHENTICATION_INITIALIZER_SERVICE]);

ngmodule.directive('userMenu', function(authenticationInitializer: any) {
  return {
    restrict: 'E',
    replace: true,
    templateUrl: require('./userMenu.directive.html'),
    link(scope: any) {
      scope.authEnabled = SETTINGS.authEnabled;
      scope.user = AuthenticationService.getAuthenticatedUser();
      scope.showLogOutDropdown = () => AuthenticationService.getAuthenticatedUser().authenticated;
      scope.logOut = () => authenticationInitializer.logOut();
    },
  };
});
