import { ILogService, module, toJson } from 'angular';
import { cloneDeep, uniq } from 'lodash';

import { AccountService, IAccountDetails, IAggregatedAccounts, IRegion } from '../../../account/AccountService';
import { Application } from '../../application.model';
import { ClusterMatcher, IClusterMatchRule } from '../../../cluster/ClusterRuleMatcher';
import { IConfigSectionFooterViewState } from '../footer/configSectionFooter.component';
import './trafficGuardConfig.help';
import { CLUSTER_MATCHES_COMPONENT, IClusterMatch } from '../../../widgets/cluster/clusterMatches.component';

export interface ITrafficGuardRule extends IClusterMatchRule {
  enabled: boolean;
}

export class TrafficGuardConfigController {
  public application: Application;
  public locationsByAccount: { [account: string]: string[] };
  public accounts: IAccountDetails[] = [];
  public config: ITrafficGuardRule[];
  public initializing = true;
  public clusterMatches: IClusterMatch[][] = [];

  public viewState: IConfigSectionFooterViewState = {
    originalConfig: null,
    originalStringVal: null,
    saving: false,
    saveError: false,
    isDirty: false,
  };

  public static $inject = ['$log'];
  public constructor(private $log: ILogService) {}

  public $onInit(): void {
    if (this.application.notFound || this.application.hasError) {
      return;
    }
    this.config = this.application.attributes.trafficGuards || [];
    this.viewState.originalConfig = cloneDeep(this.config);
    this.viewState.originalStringVal = toJson(this.viewState.originalConfig);

    AccountService.getCredentialsKeyedByAccount().then((aggregated: IAggregatedAccounts) => {
      const allAccounts = Object.keys(aggregated).map((name: string) => aggregated[name]);
      const accountsWithRegionsOnly = allAccounts.filter(
        (details: IAccountDetails) => details.regions && !details.namespaces,
      );
      const accountsWithNamespacesOnly = allAccounts.filter(
        (details: IAccountDetails) => details.namespaces && !details.regions,
      );

      const unsupportedAccounts = allAccounts.filter(
        (details: IAccountDetails) => details.regions && details.namespaces,
      );
      if (unsupportedAccounts.length) {
        this.$log.warn(
          'Account(s) ',
          unsupportedAccounts,
          ' have both namespaces and regions - this is not supported.',
        );
      }

      this.accounts = accountsWithRegionsOnly.concat(accountsWithNamespacesOnly);
      this.locationsByAccount = {};
      accountsWithRegionsOnly.forEach((details: IAccountDetails) => {
        this.locationsByAccount[details.name] = ['*'].concat(details.regions.map((region: IRegion) => region.name));
      });

      accountsWithNamespacesOnly.forEach((details: IAccountDetails) => {
        this.locationsByAccount[details.name] = ['*'].concat(details.namespaces);
      });

      this.application
        .getDataSource('serverGroups')
        .ready()
        .then(() => this.configureMatches());
      this.initializing = false;
    });
  }

  public addGuard(): void {
    this.config.push({ enabled: true, account: null, location: null, stack: null, detail: null });
    this.configChanged();
  }

  public removeGuard(index: number): void {
    this.config.splice(index, 1);
    this.configChanged();
  }

  public configChanged(): void {
    this.configureMatches();
    this.viewState.isDirty = this.viewState.originalStringVal !== toJson(this.config);
  }

  public configureMatches(): void {
    this.clusterMatches.length = 0;
    this.config.forEach((guard) => {
      this.clusterMatches.push(
        this.application.clusters
          .filter((c) =>
            c.serverGroups.some((s) => ClusterMatcher.getMatchingRule(c.account, s.region, c.name, [guard]) !== null),
          )
          .map((c) => {
            return {
              name: c.name,
              account: guard.account,
              regions: guard.location === '*' ? uniq(c.serverGroups.map((g) => g.region)).sort() : [guard.location],
            };
          }),
      );
    });
    this.clusterMatches.forEach((m) => m.sort((a: IClusterMatch, b: IClusterMatch) => a.name.localeCompare(b.name)));
  }
}

const trafficGuardConfigComponent: ng.IComponentOptions = {
  bindings: {
    application: '=',
  },
  controller: TrafficGuardConfigController,
  templateUrl: require('./trafficGuardConfig.component.html'),
};

export const TRAFFIC_GUARD_CONFIG_COMPONENT = 'spinnaker.core.application.config.trafficGuard.component';
module(TRAFFIC_GUARD_CONFIG_COMPONENT, [CLUSTER_MATCHES_COMPONENT]).component(
  'trafficGuardConfig',
  trafficGuardConfigComponent,
);
