import {extend, module, IScope, IControllerService} from 'angular';
import {IStateService} from 'angular-ui-router';

import {NamingService} from 'core/naming/naming.service';

class AppengineServerGroupBasicSettingsCtrl {
  static get $inject() { return ['$scope', '$state', '$controller', '$uibModalStack', 'namingService']; }

  constructor($scope: IScope,
              $state: IStateService,
              $controller: IControllerService,
              $uibModalStack: any,
              namingService: NamingService) {

    extend(this, $controller('BasicSettingsMixin', {
      $scope: $scope,
      imageReader: null,
      namingService: namingService,
      $uibModalStack: $uibModalStack,
      $state: $state,
    }));
  }
}

export const APPENGINE_SERVER_GROUP_BASIC_SETTINGS_CTRL = 'spinnaker.appengine.basicSettings.controller';

module(APPENGINE_SERVER_GROUP_BASIC_SETTINGS_CTRL, [])
  .controller('appengineServerGroupBasicSettingsCtrl', AppengineServerGroupBasicSettingsCtrl);
