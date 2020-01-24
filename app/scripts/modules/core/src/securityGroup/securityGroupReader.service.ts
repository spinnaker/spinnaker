import { filter, forOwn, has, uniq } from 'lodash';
import { module, IPromise, ILogService, IQService } from 'angular';

import { API } from 'core/api/ApiService';
import { IComponentName, NameUtils } from 'core/naming';
import { InfrastructureCaches } from 'core/cache';
import { Application } from 'core/application/application.model';
import { ISecurityGroup, ILoadBalancer, IServerGroup, IServerGroupUsage } from 'core/domain';
import {
  SECURITY_GROUP_TRANSFORMER_SERVICE,
  SecurityGroupTransformerService,
} from './securityGroupTransformer.service';
import { EntityTagsReader } from 'core/entityTag/EntityTagsReader';
import { SETTINGS } from 'core/config/settings';
import { SearchService, ISearchResults } from 'core/search/search.service';
import { ISecurityGroupSearchResult } from './securityGroupSearchResultType';
import { ProviderServiceDelegate, PROVIDER_SERVICE_DELEGATE } from 'core/cloudProvider/providerService.delegate';
import { IMoniker } from 'core/naming/IMoniker';
import { IEntityTags } from 'core/domain/IEntityTags';

export interface ISecurityGroupsByAccount {
  [account: string]: {
    [region: string]: {
      [name: string]: ISecurityGroup;
    };
  };
}

export interface IApplicationSecurityGroup {
  name: string;
}

export interface IReaderSecurityGroup extends ISecurityGroup {
  securityGroups: {
    [region: string]: ISecurityGroup[];
  };
}

export interface IRangeRule {
  portRanges: Array<{
    startPort: number;
    endPort: number;
  }>;
  protocol: string;
  description: string;
}

export interface ISecurityGroupRule extends IRangeRule {
  securityGroup: ISecurityGroup;
}

export interface IAddressableRange {
  ip: string;
  cidr: string;
}

export interface IIPRangeRule extends IRangeRule {
  range: IAddressableRange;
}

export interface ISecurityGroupProcessorResult {
  notFoundCaught: boolean;
  securityGroups: ISecurityGroup[];
}

export interface ISecurityGroupSummary {
  id: string;
  name: string;
  vpcId: string;
  moniker?: IMoniker;
}

export interface ISecurityGroupsByAccountSourceData {
  [account: string]: {
    [provider: string]: {
      [region: string]: ISecurityGroupSummary[];
    };
  };
}

export interface ISecurityGroupDetail {
  inboundRules: ISecurityGroupRule[] & IIPRangeRule[];
  ipRangeRules: ISecurityGroupRule[];
  region: string;
  name: string;
  entityTags: IEntityTags;
  securityGroupRules: ISecurityGroupRule[];
}

export class SecurityGroupReader {
  private static indexSecurityGroups(securityGroups: IReaderSecurityGroup[]): ISecurityGroupsByAccount {
    const securityGroupIndex: ISecurityGroupsByAccount = {};
    securityGroups.forEach((securityGroup: IReaderSecurityGroup) => {
      const accountName: string = securityGroup.account;
      securityGroupIndex[accountName] = {};
      const accountIndex = securityGroupIndex[accountName];
      forOwn(securityGroup.securityGroups, (groups: ISecurityGroup[], region: string) => {
        const regionIndex: { [key: string]: ISecurityGroup } = {};
        accountIndex[region] = regionIndex;
        groups.forEach((group: ISecurityGroup) => {
          group.accountName = accountName;
          group.region = region;
          regionIndex[group.id] = group;
          regionIndex[group.name] = group;
        });
      });
    });

    return securityGroupIndex;
  }

  private static attachUsageFields(securityGroup: ISecurityGroup): void {
    if (!securityGroup.usages) {
      securityGroup.usages = {
        loadBalancers: [],
        serverGroups: [],
      };
    }
  }

  private static sortUsages(securityGroup: ISecurityGroup): void {
    if (!securityGroup.usages) {
      return;
    }
    // reverse sort - it's gross but keeps versions mostly sorted in the chronological order
    securityGroup.usages.serverGroups.sort((a, b) => b.name.localeCompare(a.name));
    // reverse sort - gross but what we are doing now and consistent with the server groups
    securityGroup.usages.loadBalancers.sort((a, b) => b.name.localeCompare(a.name));
  }

  private resolve(index: any, container: ISecurityGroup, securityGroupId: string): any {
    return this.providerServiceDelegate
      .getDelegate<any>(container.provider || container.type || container.cloudProvider, 'securityGroup.reader')
      .resolveIndexedSecurityGroup(index, container, securityGroupId);
  }

  private addLoadBalancerSecurityGroups(application: Application): ISecurityGroupProcessorResult {
    let notFoundCaught = false;
    const securityGroups: ISecurityGroup[] = [];
    application.getDataSource('loadBalancers').data.forEach((loadBalancer: ILoadBalancer) => {
      if (loadBalancer.securityGroups) {
        loadBalancer.securityGroups.forEach((securityGroupId: string) => {
          try {
            const securityGroup: ISecurityGroup = this.resolve(
              application['securityGroupsIndex'],
              loadBalancer,
              securityGroupId,
            );
            SecurityGroupReader.attachUsageFields(securityGroup);
            if (!securityGroup.usages.loadBalancers.some(lb => lb.name === loadBalancer.name)) {
              securityGroup.usages.loadBalancers.push({ name: loadBalancer.name });
            }
            securityGroups.push(securityGroup);
          } catch (e) {
            this.$log.warn('could not attach firewall to load balancer:', loadBalancer.name, securityGroupId, e);
            notFoundCaught = true;
          }
        });
      }
    });
    securityGroups.forEach(SecurityGroupReader.sortUsages);

    return { notFoundCaught, securityGroups };
  }

  private addNameBasedSecurityGroups(
    application: Application,
    nameBasedSecurityGroups: ISecurityGroup[],
  ): ISecurityGroupProcessorResult {
    let notFoundCaught = false;
    const securityGroups: ISecurityGroup[] = [];
    nameBasedSecurityGroups.forEach((securityGroup: ISecurityGroup) => {
      try {
        const match: ISecurityGroup = this.resolve(application['securityGroupsIndex'], securityGroup, securityGroup.id);
        SecurityGroupReader.attachUsageFields(match);
        securityGroups.push(match);
      } catch (e) {
        this.$log.warn('could not initialize application firewall:', securityGroup);
        notFoundCaught = true;
      }
    });

    return { notFoundCaught, securityGroups };
  }

  private addServerGroupSecurityGroups(application: Application): ISecurityGroupProcessorResult {
    let notFoundCaught = false;
    const securityGroups: ISecurityGroup[] = [];
    application.getDataSource('serverGroups').data.forEach((serverGroup: IServerGroup) => {
      if (serverGroup.securityGroups) {
        serverGroup.securityGroups.forEach((securityGroupId: string) => {
          try {
            const securityGroup: ISecurityGroup = this.resolve(
              application['securityGroupsIndex'],
              serverGroup,
              securityGroupId,
            );
            SecurityGroupReader.attachUsageFields(securityGroup);
            if (!securityGroup.usages.serverGroups.some((sg: IServerGroupUsage) => sg.name === serverGroup.name)) {
              const { account, isDisabled, name, cloudProvider, region } = serverGroup;
              securityGroup.usages.serverGroups.push({ account, isDisabled, name, cloudProvider, region });
            }
            securityGroups.push(securityGroup);
          } catch (e) {
            this.$log.warn('could not attach firewall to server group:', serverGroup.name, securityGroupId);
            notFoundCaught = true;
          }
        });
      }
    });
    securityGroups.forEach(SecurityGroupReader.sortUsages);

    return { notFoundCaught, securityGroups };
  }

  private clearCacheAndRetryAttachingSecurityGroups(
    application: Application,
    nameBasedSecurityGroups: ISecurityGroup[],
  ): IPromise<any[]> {
    InfrastructureCaches.clearCache('securityGroups');
    return this.loadSecurityGroups().then((refreshedSecurityGroups: ISecurityGroupsByAccount) => {
      application['securityGroupsIndex'] = refreshedSecurityGroups;

      return this.attachSecurityGroups(application, nameBasedSecurityGroups, false);
    });
  }

  private addNamePartsToSecurityGroup(securityGroup: ISecurityGroup): void {
    const nameParts: IComponentName = NameUtils.parseSecurityGroupName(securityGroup.name);
    securityGroup.stack = nameParts.stack;
    securityGroup.detail = nameParts.freeFormDetails;
    securityGroup.moniker = NameUtils.getMoniker(nameParts.application, nameParts.stack, nameParts.freeFormDetails);
  }

  private attachSecurityGroups(
    application: Application,
    nameBasedSecurityGroups: ISecurityGroup[],
    retryIfNotFound: boolean,
  ): IPromise<any[]> {
    let data: ISecurityGroup[] = [];
    let notFoundCaught = false;
    if (nameBasedSecurityGroups) {
      // reset everything
      application.getDataSource('securityGroups').data = [];
      const nameBasedGroups: ISecurityGroupProcessorResult = this.addNameBasedSecurityGroups(
        application,
        nameBasedSecurityGroups,
      );
      notFoundCaught = nameBasedGroups.notFoundCaught;
      if (!nameBasedGroups.notFoundCaught) {
        data = nameBasedGroups.securityGroups;
      }
    } else {
      // filter down to empty (name-based only) firewalls - we will repopulate usages
      data = application
        .getDataSource('securityGroups')
        .data.filter(
          (group: ISecurityGroup) => !group.usages.serverGroups.length && !group.usages.loadBalancers.length,
        );
    }

    if (!notFoundCaught) {
      const loadBalancerSecurityGroups: ISecurityGroupProcessorResult = this.addLoadBalancerSecurityGroups(application);
      notFoundCaught = loadBalancerSecurityGroups.notFoundCaught;
      if (!notFoundCaught) {
        data = data.concat(loadBalancerSecurityGroups.securityGroups.filter((sg: any) => !data.includes(sg)));
        const serverGroupSecurityGroups: ISecurityGroupProcessorResult = this.addServerGroupSecurityGroups(application);
        notFoundCaught = serverGroupSecurityGroups.notFoundCaught;
        if (!notFoundCaught) {
          data = data.concat(serverGroupSecurityGroups.securityGroups.filter((sg: any) => !data.includes(sg)));
        }
      }
    }

    data = uniq(data);
    if (notFoundCaught && retryIfNotFound) {
      this.$log.warn('Clearing firewall cache and trying again...');
      return this.clearCacheAndRetryAttachingSecurityGroups(application, nameBasedSecurityGroups);
    } else {
      data.forEach((sg: ISecurityGroup) => this.addNamePartsToSecurityGroup(sg));
      return this.$q
        .all(data.map((sg: ISecurityGroup) => this.securityGroupTransformer.normalizeSecurityGroup(sg)))
        .then(() => data);
    }
  }

  public static $inject = ['$log', '$q', 'securityGroupTransformer', 'providerServiceDelegate'];
  constructor(
    private $log: ILogService,
    private $q: IQService,
    private securityGroupTransformer: SecurityGroupTransformerService,
    private providerServiceDelegate: ProviderServiceDelegate,
  ) {}

  public getAllSecurityGroups(): IPromise<ISecurityGroupsByAccountSourceData> {
    const cache = InfrastructureCaches.get('securityGroups');
    const cached = cache ? cache.get('allGroups') : null;
    if (cached) {
      return this.$q.resolve(this.decompress(cached));
    }
    return API.one('securityGroups')
      .get()
      .then((groupsByAccount: ISecurityGroupsByAccountSourceData) => {
        if (cache) {
          cache.put('allGroups', this.compress(groupsByAccount));
        }
        return groupsByAccount;
      });
  }

  private compress(data: ISecurityGroupsByAccountSourceData): any {
    const compressed: any = {};
    Object.keys(data).forEach(account => {
      compressed[account] = {};
      Object.keys(data[account]).forEach(provider => {
        compressed[account][provider] = {};
        Object.keys(data[account][provider]).forEach(region => {
          // Because these are cached in local storage, we unfortunately need to remove the moniker, as it triples the size
          // of the object being stored, which blows out our LS quota for a sufficiently large footprint
          data[account][provider][region].forEach(group => delete group.moniker);
        });
        if (this.providerServiceDelegate.hasDelegate(provider, 'securityGroup.transformer')) {
          const service: any = this.providerServiceDelegate.getDelegate(provider, 'securityGroup.transformer');
          if (service.supportsCompression) {
            Object.keys(data[account][provider]).forEach(region => {
              compressed[account][provider][region] = service.compress(data[account][provider][region]);
            });
          } else {
            compressed[account][provider] = data[account][provider];
          }
        } else {
          compressed[account][provider] = data[account][provider];
        }
      });
    });
    return compressed;
  }

  private decompress(data: any): ISecurityGroupsByAccountSourceData {
    Object.keys(data).forEach(account => {
      Object.keys(data[account]).forEach(provider => {
        if (this.providerServiceDelegate.hasDelegate(provider, 'securityGroup.transformer')) {
          const service: any = this.providerServiceDelegate.getDelegate(provider, 'securityGroup.transformer');
          if (service && service.supportsCompression) {
            Object.keys(data[account][provider]).forEach(region => {
              data[account][provider][region] = service.decompress(data[account][provider][region]);
            });
          }
        }
      });
    });
    return data;
  }

  public getApplicationSecurityGroup(
    application: Application,
    account: string,
    region: string,
    id: string,
  ): IApplicationSecurityGroup {
    let result: IApplicationSecurityGroup = null;
    if (has(application['securityGroupsIndex'], [account, region, id])) {
      result = application['securityGroupsIndex'][account][region][id];
    }

    return result;
  }

  public getApplicationSecurityGroups(
    application: Application,
    nameBasedSecurityGroups: ISecurityGroup[],
  ): IPromise<any> {
    return this.loadSecurityGroups()
      .then((allSecurityGroups: ISecurityGroupsByAccount) => {
        application['securityGroupsIndex'] = allSecurityGroups;
      })
      .then(() =>
        this.$q
          .all([application.getDataSource('serverGroups').ready(), application.getDataSource('loadBalancers').ready()])
          .then(() => this.attachSecurityGroups(application, nameBasedSecurityGroups, true)),
      );
  }

  public getSecurityGroupDetails(
    application: Application,
    account: string,
    provider: string,
    region: string,
    vpcId: string,
    id: string,
  ): IPromise<ISecurityGroupDetail> {
    return API.one('securityGroups')
      .one(account)
      .one(region)
      .one(id)
      .withParams({ provider, vpcId })
      .get()
      .then((details: ISecurityGroupDetail) => {
        if (details && details.inboundRules) {
          details.ipRangeRules = details.inboundRules.filter((rule: ISecurityGroupRule & IIPRangeRule) => rule.range);
          details.securityGroupRules = details.inboundRules.filter((rule: ISecurityGroupRule) => rule.securityGroup);
          details.securityGroupRules.forEach((inboundRule: ISecurityGroupRule) => {
            const inboundGroup = inboundRule.securityGroup;
            if (!inboundGroup.name) {
              const applicationSecurityGroup: IApplicationSecurityGroup = this.getApplicationSecurityGroup(
                application,
                inboundGroup.accountName,
                details.region,
                inboundGroup.id,
              );
              if (applicationSecurityGroup) {
                inboundGroup.name = applicationSecurityGroup.name;
              } else {
                inboundGroup.name = inboundGroup.id;
                inboundGroup.inferredName = true;
              }
            }
          });
        }
        if (SETTINGS.feature.entityTags && application.isStandalone) {
          return EntityTagsReader.getEntityTagsForId('securitygroup', details.name).then(tags => {
            details.entityTags = tags.find(
              t =>
                t.entityRef.entityId === details.name &&
                t.entityRef['account'] === account &&
                t.entityRef['region'] === region,
            );
            return details;
          });
        }
        return details;
      });
  }

  public loadSecurityGroups(): IPromise<ISecurityGroupsByAccount> {
    return this.getAllSecurityGroups().then((groupsByAccount: ISecurityGroupsByAccountSourceData) => {
      const securityGroups: IReaderSecurityGroup[] = [];
      forOwn(groupsByAccount, (groupsByProvider, account) => {
        return forOwn(groupsByProvider, (groupsByRegion, provider) => {
          forOwn(groupsByRegion, (groups: ISecurityGroup[]) => {
            groups.forEach(group => {
              group.provider = provider;
              group.account = account;
            });
          });
          securityGroups.push({ account, provider, securityGroups: groupsByProvider[provider] });
        });
      });

      return SecurityGroupReader.indexSecurityGroups(securityGroups);
    });
  }

  public loadSecurityGroupsByApplicationName(applicationName: string): IPromise<ISecurityGroup[]> {
    return SearchService.search<ISecurityGroupSearchResult>({
      q: applicationName,
      type: 'securityGroups',
      pageSize: 1000,
    }).then((searchResults: ISearchResults<ISecurityGroupSearchResult>) => {
      let result: ISecurityGroup[] = [];
      if (!searchResults || !searchResults.results) {
        this.$log.warn('WARNING: Gate firewall endpoint appears to be down.');
      } else {
        result = filter(searchResults.results, { application: applicationName });
      }

      return result;
    });
  }
}

export const SECURITY_GROUP_READER = 'spinnaker.core.securityGroup.read.service';
module(SECURITY_GROUP_READER, [SECURITY_GROUP_TRANSFORMER_SERVICE, PROVIDER_SERVICE_DELEGATE]).service(
  'securityGroupReader',
  SecurityGroupReader,
);
