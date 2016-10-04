import {module} from 'angular';

import './chaosMonkeyExceptions.component.less';

export class ChaosMonkeyExceptionsController {

  static get $inject() { return ['accountService', '$q']; }

  public accounts: any[] = [];
  public regionsByAccount: any;
  public config: any;
  public configChanged: () => void;

  public constructor(private accountService: any, private $q: ng.IQService) {}

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
    this.accountService.listAccounts().then((accounts: any[]) => {
      this.$q.all(accounts.map((account: any) => this.accountService.getAccountDetails(account.name)))
        .then((details) => {
          this.accounts = details.filter((account: any) => account.regions );
          this.regionsByAccount = {};
          this.accounts.forEach((account: any) => {
            this.regionsByAccount[account.name] = ['*'].concat(account.regions.map((region: any) => region.name));
          });
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

module(moduleName, [
  require('../account/account.service.js'),
])
.component('chaosMonkeyExceptions', new ChaosMonkeyExceptionsComponent());

export default moduleName;
