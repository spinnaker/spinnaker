import {IPromise} from 'angular';

import {AccountService} from 'core/account/account.service';
import {StageConstants} from 'core/pipeline/config/stages/stageConstants';
import {IAppengineAccount, IAppengineStageScope} from 'appengine/domain/index';

export class AppengineStageCtrl {
  constructor(public $scope: IAppengineStageScope, private accountService: AccountService) {}

  public setStageRegion(): void {
    let selected = this.$scope.accounts.find((account) => account.name === this.$scope.stage.credentials);
    if (selected && selected.name) {
      this.accountService.getAccountDetails(selected.name)
        .then((accountDetails: IAppengineAccount) => {
          this.$scope.stage.region = accountDetails.region;
        });
    }
  }

  protected setStageCloudProvider(): void {
    this.$scope.stage.cloudProvider = 'appengine';
  }

  protected setAccounts(): IPromise<void> {
    return this.accountService.listAccounts('appengine').then((accounts: IAppengineAccount[]) => {
      this.$scope.accounts = accounts;
    });
  }

  protected setTargets(): void {
    this.$scope.targets = StageConstants.TARGET_LIST;

    if (!this.$scope.stage.target) {
      this.$scope.stage.target = this.$scope.targets[0].val;
    }
  }
}
