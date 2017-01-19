import {extend, module, IScope, IControllerService} from 'angular';
import {IStateService} from 'angular-ui-router';
import {set} from 'lodash';

import {NamingService} from 'core/naming/naming.service';
import {IAppengineServerGroupCommand} from '../serverGroupCommandBuilder.service';

interface IAppengineBasicSettingsScope extends IScope {
  command: IAppengineServerGroupCommand;
}

class AppengineServerGroupBasicSettingsCtrl {
  static get $inject() { return ['$scope', '$state', '$controller', '$uibModalStack', 'namingService']; }

  constructor(public $scope: IAppengineBasicSettingsScope,
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

  public toggleResolveViaTrigger(): void {
    this.$scope.command.fromTrigger = !this.$scope.command.fromTrigger;
    delete this.$scope.command.trigger;
    delete this.$scope.command.branch;
  }

  public onTriggerChange() {
    set(this, '$scope.command.trigger.matchBranchOnRegex', undefined);
  }
}

export const APPENGINE_SERVER_GROUP_BASIC_SETTINGS_CTRL = 'spinnaker.appengine.basicSettings.controller';

module(APPENGINE_SERVER_GROUP_BASIC_SETTINGS_CTRL, [])
  .controller('appengineServerGroupBasicSettingsCtrl', AppengineServerGroupBasicSettingsCtrl);
