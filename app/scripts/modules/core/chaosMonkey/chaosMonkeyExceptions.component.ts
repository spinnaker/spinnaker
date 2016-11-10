import {module} from 'angular';

import {
  ACCOUNT_SERVICE, AccountService, IAccountDetails, IRegion,
  IAggregatedAccounts
} from 'core/account/account.service';
import './chaosMonkeyExceptions.component.less';

export class ChaosMonkeyExceptionsController {

  static get $inject() { return ['accountService', '$q']; }

  public accounts: IAccountDetails[] = [];
  public regionsByAccount: any;
  public config: any;
  public configChanged: () => void;

  public constructor(private accountService: AccountService, private $q: ng.IQService) {}

  public addException(): void {
    this.config.exceptions = this.config.exceptions || [];
    this.config.exceptions.push({});
    this.configChanged();
  };

  public removeException(index: number): void {
    this.config.exceptions.splice(index, 1);
    this.configChanged();
  };

  public $onInit(): void {
    this.accountService.getCredentialsKeyedByAccount().then((aggregated: IAggregatedAccounts) => {
      this.accounts = Object.keys(aggregated)
        .map((name: string) => aggregated[name])
        .filter((details: IAccountDetails) => details.regions);
      this.regionsByAccount = {};
      this.accounts.forEach((details: IAccountDetails) => {
        this.regionsByAccount[details.name] = ['*'].concat(details.regions.map((region: IRegion) => region.name));
      });
    });
  }
}

class ChaosMonkeyExceptionsComponent implements ng.IComponentOptions {
  public bindings: any = {
    config: '=',
    configChanged: '&',
  };
  public controller: ng.IComponentController = ChaosMonkeyExceptionsController;
  public templateUrl: string = require('./chaosMonkeyExceptions.component.html');
}

const moduleName = 'spinnaker.core.chaosMonkey.exceptions.directive';

module(moduleName, [ACCOUNT_SERVICE])
.component('chaosMonkeyExceptions', new ChaosMonkeyExceptionsComponent());

export default moduleName;
