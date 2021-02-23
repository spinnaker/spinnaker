import { module } from 'angular';
import { SETTINGS } from 'core/config/settings';

import { AuthenticationInitializer } from '../AuthenticationInitializer';
import { AuthenticationService } from '../AuthenticationService';

export const AUTHENTICATION_USER_MENU = 'spinnaker.core.authentication.userMenu.directive';

const ngmodule = module(AUTHENTICATION_USER_MENU, []);

ngmodule.directive('userMenu', function () {
  return {
    restrict: 'E',
    replace: true,
    templateUrl: require('./userMenu.directive.html'),
    link(scope: any) {
      scope.authEnabled = SETTINGS.authEnabled;
      scope.user = AuthenticationService.getAuthenticatedUser();
      scope.showLogOutDropdown = () => AuthenticationService.getAuthenticatedUser().authenticated;
      scope.logOut = () => AuthenticationInitializer.logOut();
    },
  };
});
