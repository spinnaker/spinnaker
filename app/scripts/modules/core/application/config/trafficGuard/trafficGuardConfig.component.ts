import {module, toJson} from 'angular';
import {cloneDeep} from 'lodash';

import {
  ACCOUNT_SERVICE, AccountService, IAccountDetails, IRegion,
  IAggregatedAccounts
} from 'core/account/account.service';
import {Application} from 'core/application/application.model';
import {IViewState} from '../footer/configSectionFooter.component';
import {TRAFFIC_GUARD_CONFIG_HELP} from './trafficGuardConfig.help';

interface ITrafficGuard {
  account: string;
  location: string;
  stack: string;
  detail: string;
}

export class TrafficGuardConfigController {

  public application: Application;
  public accounts: IAccountDetails[] = [];
  public regionsByAccount: { [account: string]: string[] };
  public config: ITrafficGuard[];
  public initializing: boolean = true;

  public viewState: IViewState = {
    originalConfig: null,
    originalStringVal: null,
    saving: false,
    saveError: false,
    isDirty: false,
  };

  static get $inject() { return ['accountService']; }

  public constructor(private accountService: AccountService) {}

  public $onInit(): void {
    if (this.application.notFound) {
      return;
    }
    this.config = this.application.attributes.trafficGuards || [];
    this.viewState.originalConfig = cloneDeep(this.config);
    this.viewState.originalStringVal = toJson(this.viewState.originalConfig);

    this.accountService.getCredentialsKeyedByAccount().then((aggregated: IAggregatedAccounts) => {
      this.accounts = Object.keys(aggregated)
        .map((name: string) => aggregated[name])
        .filter((details: IAccountDetails) => details.regions);
      this.regionsByAccount = {};
      this.accounts.forEach((details: IAccountDetails) => {
        this.regionsByAccount[details.name] = ['*'].concat(details.regions.map((region: IRegion) => region.name));
      });
      this.initializing = false;
    });
  }

  public addGuard(): void {
    this.config.push({ account: null, location: null, stack: null, detail: null });
    this.configChanged();
  };

  public removeGuard(index: number): void {
    this.config.splice(index, 1);
    this.configChanged();
  };

  public configChanged(): void {
    this.viewState.isDirty = this.viewState.originalStringVal !== toJson(this.config);
  }
}

class TrafficGuardConfigComponent implements ng.IComponentOptions {
  public bindings: any = {
    application: '=',
  };
  public controller: any = TrafficGuardConfigController;
  public templateUrl: string = require('./trafficGuardConfig.component.html');
}

export const TRAFFIC_GUARD_CONFIG_COMPONENT = 'spinnaker.core.application.config.trafficGuard.component';
module(TRAFFIC_GUARD_CONFIG_COMPONENT, [
  ACCOUNT_SERVICE,
  TRAFFIC_GUARD_CONFIG_HELP,
])
  .component('trafficGuardConfig', new TrafficGuardConfigComponent());
