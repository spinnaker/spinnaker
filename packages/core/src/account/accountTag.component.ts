import { IComponentOptions, IController, module } from 'angular';

import { AccountService } from './AccountService';

import './accountTag.less';

class AccountTagController implements IController {
  public account: string;

  public accountType: string;

  public $onInit(): void {
    AccountService.challengeDestructiveActions(this.account).then((isProdAccount) => {
      this.accountType = isProdAccount ? 'prod' : this.account;
    });
  }

  public $onChanges(): void {
    this.$onInit();
  }
}

export const accountTagComponent: IComponentOptions = {
  bindings: {
    account: '<',
  },
  controller: AccountTagController,
  template: '<span class="account-tag account-tag-{{$ctrl.accountType}}">{{$ctrl.account}}</span>',
};

export const ACCOUNT_TAG_COMPONENT = 'spinnaker.core.account.accountTag';
module(ACCOUNT_TAG_COMPONENT, []).component('accountTag', accountTagComponent);
