import { extend, IControllerService, IScope, module } from 'angular';
import { StateService } from 'angular-ui-router';
import { set } from 'lodash';

import { NamingService } from '@spinnaker/core';

import { GitCredentialType, IAppengineAccount } from 'appengine/domain/index';
import { IAppengineServerGroupCommand } from '../serverGroupCommandBuilder.service';

interface IAppengineBasicSettingsScope extends IScope {
  command: IAppengineServerGroupCommand;
}

class AppengineServerGroupBasicSettingsCtrl {
  constructor(public $scope: IAppengineBasicSettingsScope,
              $state: StateService,
              $controller: IControllerService,
              $uibModalStack: any,
              namingService: NamingService) {
    'ngInject';

    extend(this, $controller('BasicSettingsMixin', {
      $scope: $scope,
      imageReader: null,
      namingService: namingService,
      $uibModalStack: $uibModalStack,
      $state: $state,
    }));

    if (!this.$scope.command.gitCredentialType) {
      this.onAccountChange();
    }
  }

  public toggleResolveViaTrigger(): void {
    this.$scope.command.fromTrigger = !this.$scope.command.fromTrigger;
    delete this.$scope.command.trigger;
    delete this.$scope.command.branch;
  }

  public onTriggerChange(): void {
    set(this, '$scope.command.trigger.matchBranchOnRegex', undefined);
  }

  public onAccountChange(): void {
    const account = this.findAccountInBackingData();
    if (account) {
      this.$scope.command.gitCredentialType = account.supportedGitCredentialTypes[0];
    } else {
      this.$scope.command.gitCredentialType = 'NONE';
    }
  }

  public getSupportedGitCredentialTypes(): GitCredentialType[] {
    const account = this.findAccountInBackingData();
    if (account) {
      return account.supportedGitCredentialTypes;
    } else {
      return ['NONE'];
    }
  }

  public humanReadableGitCredentialType(type: GitCredentialType): string {
    switch (type) {
      case 'HTTPS_USERNAME_PASSWORD':
        return 'HTTPS with username and password';
      case 'HTTPS_GITHUB_OAUTH_TOKEN':
        return 'HTTPS with Github OAuth token';
      case 'SSH':
        return 'SSH';
      case 'NONE':
        return 'No credentials';
      default:
        return 'No credentials';
    }
  }

  private findAccountInBackingData(): IAppengineAccount {
    return this.$scope.command.backingData.accounts.find((account: IAppengineAccount) => {
      return this.$scope.command.credentials === account.name;
    });
  }
}

export const APPENGINE_SERVER_GROUP_BASIC_SETTINGS_CTRL = 'spinnaker.appengine.basicSettings.controller';

module(APPENGINE_SERVER_GROUP_BASIC_SETTINGS_CTRL, [])
  .controller('appengineServerGroupBasicSettingsCtrl', AppengineServerGroupBasicSettingsCtrl);
