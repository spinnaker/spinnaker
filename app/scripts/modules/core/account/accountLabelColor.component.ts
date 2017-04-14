import {module, IComponentController, IComponentOptions} from 'angular';

import {ACCOUNT_SERVICE, AccountService} from 'core/account/account.service';

class AccountLabelColorController implements IComponentController {
  public account: string;

  public accountType: string;

  static get $inject(): string[] { return ['accountService']; }
  constructor(private accountService: AccountService) {}

  public $onInit(): void {
    this.accountService.challengeDestructiveActions(this.account).then((isProdAccount) => {
      this.accountType = isProdAccount ? 'prod' : this.account;
    });
  }
}

export class AccountLabelColorComponent implements IComponentOptions {
  public bindings: any = {
    account: '<'
  };
  public controller: any = AccountLabelColorController;
  public template = '<span class="account-tag account-tag-{{$ctrl.accountType}}">{{$ctrl.account}}</span>';
}

export const ACCOUNT_LABEL_COLOR_COMPONENT = 'spinnaker.core.account.accountLabelColor';
module(ACCOUNT_LABEL_COLOR_COMPONENT, [
  ACCOUNT_SERVICE
])
  .component('accountLabelColor', new AccountLabelColorComponent());
