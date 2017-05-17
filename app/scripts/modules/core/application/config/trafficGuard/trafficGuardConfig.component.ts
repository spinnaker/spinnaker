import { module, toJson } from 'angular';
import { cloneDeep, uniq } from 'lodash';

import {
  ACCOUNT_SERVICE,
  AccountService,
  IAccountDetails,
  IAggregatedAccounts,
  IRegion
} from 'core/account/account.service';
import { Application, IConfigSectionFooterViewState } from 'core/application';
import { NAMING_SERVICE, NamingService } from 'core/naming/naming.service';
import { CLUSTER_MATCHES_COMPONENT, IClusterMatch } from 'core/widgets/cluster/clusterMatches.component';
import { TRAFFIC_GUARD_CONFIG_HELP } from './trafficGuardConfig.help';

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
  public initializing = true;
  public clusterMatches: IClusterMatch[][] = [];

  public viewState: IConfigSectionFooterViewState = {
    originalConfig: null,
    originalStringVal: null,
    saving: false,
    saveError: false,
    isDirty: false,
  };

  public constructor(private accountService: AccountService, private namingService: NamingService) { 'ngInject'; }

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
      this.application.getDataSource('serverGroups').ready().then(() => this.configureMatches());
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
    this.configureMatches();
    this.viewState.isDirty = this.viewState.originalStringVal !== toJson(this.config);
  }

  public configureMatches(): void {
    this.clusterMatches.length = 0;
    this.config.forEach(guard => {
      this.clusterMatches.push(
      this.application.clusters.filter(c => {
        const clusterNames = this.namingService.parseClusterName(c.name);
        return (guard.account === '*' || guard.account === c.account) &&
          (guard.location === '*' || c.serverGroups.map(s => s.region).includes(guard.location)) &&
          (guard.stack === '*' || clusterNames.stack === (guard.stack || '')) &&
          (guard.detail === '*' || clusterNames.freeFormDetails === (guard.detail || ''));
        }).map(c => {
          return {
            name: c.name,
            account: guard.account,
            regions: guard.location === '*' ? uniq(c.serverGroups.map(g => g.region)).sort() : [guard.location]
          };
      })
      );
    });
    this.clusterMatches.forEach(m => m.sort((a: IClusterMatch, b: IClusterMatch) => a.name.localeCompare(b.name)));
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
  NAMING_SERVICE,
  CLUSTER_MATCHES_COMPONENT,
  TRAFFIC_GUARD_CONFIG_HELP,
])
  .component('trafficGuardConfig', new TrafficGuardConfigComponent());
