import {module, IComponentController, IComponentOptions} from 'angular';

import {ACCOUNT_SERVICE, AccountService} from 'core/account/account.service';

import './accountTag.less';

class AccountTagController implements IComponentController {
  public account: string;

  public accountType: string;

  constructor(private accountService: AccountService) { 'ngInject'; }

  public $onInit(): void {
    this.accountService.challengeDestructiveActions(this.account).then((isProdAccount) => {
      this.accountType = isProdAccount ? 'prod' : this.account;
    });
  }
}

export class AccountTagComponent implements IComponentOptions {
  public bindings: any = {
    account: '<',
  };
  public controller: any = AccountTagController;
  public template = '<span class="account-tag account-tag-{{$ctrl.accountType}}">{{$ctrl.account}}</span>';
}

export const ACCOUNT_TAG_COMPONENT = 'spinnaker.core.account.accountTag';
module(ACCOUNT_TAG_COMPONENT, [
  ACCOUNT_SERVICE
])
  .component('accountTag', new AccountTagComponent());
