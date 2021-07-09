import { IController } from 'angular';

import { AccountService, StageConstants } from '@spinnaker/core';
import { AppengineHealth } from '../../common/appengineHealth';
import { IAppengineAccount, IAppengineStageScope } from '../../domain';

export class AppengineStageCtrl implements IController {
  public static $inject = ['$scope'];
  constructor(protected $scope: IAppengineStageScope) {
    $scope.platformHealth = AppengineHealth.PLATFORM;
  }

  public setStageRegion(): void {
    const selected = this.$scope.accounts.find((account) => account.name === this.$scope.stage.credentials);
    if (selected && selected.name) {
      AccountService.getAccountDetails(selected.name).then((accountDetails: IAppengineAccount) => {
        this.$scope.stage.region = accountDetails.region;
      });
    }
  }

  protected setStageCloudProvider(): void {
    this.$scope.stage.cloudProvider = 'appengine';
  }

  protected setAccounts(): PromiseLike<void> {
    return AccountService.listAccounts('appengine').then((accounts: IAppengineAccount[]) => {
      this.$scope.accounts = accounts;
    });
  }

  protected setTargets(): void {
    this.$scope.targets = StageConstants.TARGET_LIST;

    if (!this.$scope.stage.target) {
      this.$scope.stage.target = this.$scope.targets[0].val;
    }
  }

  protected setStageCredentials(): void {
    if (!this.$scope.stage.credentials && this.$scope.application.defaultCredentials.appengine) {
      this.$scope.stage.credentials = this.$scope.application.defaultCredentials.appengine;
    }
  }
}
