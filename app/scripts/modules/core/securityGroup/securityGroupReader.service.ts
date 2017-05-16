import {filter, forOwn, has, uniq} from 'lodash';
import {module, IPromise, ILogService, IQService} from 'angular';

import {API_SERVICE, Api} from 'core/api/api.service';
import {NAMING_SERVICE, NamingService, IComponentName} from 'core/naming/naming.service';
import {INFRASTRUCTURE_CACHE_SERVICE, InfrastructureCacheService} from 'core/cache/infrastructureCaches.service';
import {Application} from 'core/application/application.model';
import {ISecurityGroup, ILoadBalancer, IServerGroup, IServerGroupUsage} from 'core/domain';
import {SECURITY_GROUP_TRANSFORMER_SERVICE, SecurityGroupTransformerService} from './securityGroupTransformer.service';
import {ENTITY_TAGS_READ_SERVICE, EntityTagsReader} from 'core/entityTag/entityTags.read.service';
import {SETTINGS} from 'core/config/settings';
import {SEARCH_SERVICE, SearchService, ISearchResults} from 'core/search/search.service';
import { ISecurityGroupSearchResult } from './SecurityGroupSearchResultFormatter';

interface IRegionAccount {
  account: string;
  accountName: string;
  id: string;
  name: string;
  provider: string;
  region: string;
  vpcId: string;
}

interface IRegions {
  [key: string]: IRegionAccount[]; // regionName
}

interface IProviders {
  [key: string]: IRegions; // providerName
}

interface IGroupsByRegion {
  [key: string]: IRegionAccount[]; // regionName
}

interface IGroupsByProvider {
  [key: string]: IGroupsByRegion; // providerName
}

interface ISecurityGroupsByRegion {
  [key: string]: IRegionAccount;  // regionName
}

interface ISecurityGroupsByAccount {
  [key: string]: ISecurityGroupsByRegion;  // accountName
}

interface IIndexedSecurityGroups {
  [key: string]: ISecurityGroupsByAccount;
}

export interface IApplicationSecurityGroup {
  name: string;
}

interface IReaderSecurityGroup extends ISecurityGroup {
  securityGroups: IGroupsByRegion;
}

interface ISecurityGroupDetailInboundRule {
  accountName?: string;
  id?: string;
  inferredName?: boolean;
  name?: string;
}

interface IPortRange {
  startPort: number;
  endPort: number;
}

interface IRangeRule {
  portRanges: IPortRange[];
  protocol: string;
}

interface ISecurityGroupRule extends IRangeRule {
  securityGroup: ISecurityGroup;
}

interface IAddressableRange {
  ip: string;
  cidr: string;
}

interface IIPRangeRule extends IRangeRule {
  range: IAddressableRange;
}

interface ISecurityGroupProcessorResult {
  notFoundCaught: boolean;
  securityGroups: ISecurityGroup[];
}

export interface IGroupsByAccount {
  [key: string]: IProviders; // accountName
}

export interface ISecurityGroupDetail {
  inboundRules: ISecurityGroupRule[] & IIPRangeRule[];
  ipRangeRules: ISecurityGroupRule[];
  region: string;
  securityGroupRules: ISecurityGroupRule[];
}

export class SecurityGroupReader {

  private static indexSecurityGroups(securityGroups: IReaderSecurityGroup[]): IIndexedSecurityGroups {

    const securityGroupIndex: IIndexedSecurityGroups = {};
    securityGroups.forEach((securityGroup: IReaderSecurityGroup) => {

      const accountName: string = securityGroup.account;
      const accountIndex: ISecurityGroupsByAccount = {};
      securityGroupIndex[accountName] = accountIndex;
      forOwn(securityGroup.securityGroups, (groups: IRegionAccount[], region: string) => {

        const regionIndex: ISecurityGroupsByRegion = {};
        accountIndex[region] = regionIndex;
        groups.forEach((group: IRegionAccount) => {
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
        serverGroups: []
      };
    }
  }

  private resolve(index: any,
                  container: ISecurityGroup,
                  securityGroupId: string): any {
    return this.serviceDelegate.getDelegate(container.provider || container.type || container.cloudProvider, 'securityGroup.reader')
      .resolveIndexedSecurityGroup(index, container, securityGroupId);

  }

  private addLoadBalancerSecurityGroups(application: Application): ISecurityGroupProcessorResult {
    let notFoundCaught = false;
    const securityGroups: ISecurityGroup[] = [];
    application.getDataSource('loadBalancers').data.forEach((loadBalancer: ILoadBalancer) => {
      if (loadBalancer.securityGroups) {
        loadBalancer.securityGroups.forEach((securityGroupId: string) => {
          try {
            const securityGroup: ISecurityGroup =
              this.resolve(application['securityGroupsIndex'], loadBalancer, securityGroupId);
            SecurityGroupReader.attachUsageFields(securityGroup);
            if (!securityGroup.usages.loadBalancers.some(lb => lb.name === loadBalancer.name)) {
              securityGroup.usages.loadBalancers.push({name: loadBalancer.name});
            }
            securityGroups.push(securityGroup);
          } catch (e) {
            this.$log.warn('could not attach security group to load balancer:', loadBalancer.name, securityGroupId, e);
            notFoundCaught = true;
          }
        });
      }
    });

    return {notFoundCaught, securityGroups};
  }

  private addNameBasedSecurityGroups(application: Application,
                                     nameBasedSecurityGroups: ISecurityGroup[]): ISecurityGroupProcessorResult {

    let notFoundCaught = false;
    const securityGroups: ISecurityGroup[] = [];
    nameBasedSecurityGroups.forEach((securityGroup: ISecurityGroup) => {
      try {
        const match: ISecurityGroup = this.resolve(application['securityGroupsIndex'], securityGroup, securityGroup.id);
        SecurityGroupReader.attachUsageFields(match);
        securityGroups.push(match);
      } catch (e) {
        this.$log.warn('could not initialize application security group:', securityGroup);
        notFoundCaught = true;
      }
    });

    return {notFoundCaught, securityGroups};
  }

  private addServerGroupSecurityGroups(application: Application): ISecurityGroupProcessorResult {

    let notFoundCaught = false;
    const securityGroups: ISecurityGroup[] = [];
    application.getDataSource('serverGroups').data.forEach((serverGroup: IServerGroup) => {
      if (serverGroup.securityGroups) {
        serverGroup.securityGroups.forEach((securityGroupId: string) => {
          try {
            const securityGroup: ISecurityGroup = this.resolve(application['securityGroupsIndex'], serverGroup, securityGroupId);
            SecurityGroupReader.attachUsageFields(securityGroup);
            if (!securityGroup.usages.serverGroups.some((sg: IServerGroupUsage) => sg.name === serverGroup.name)) {
              securityGroup.usages.serverGroups.push({
                name: serverGroup.name,
                isDisabled: serverGroup.isDisabled,
                region: serverGroup.region,
              });
            }
            securityGroups.push(securityGroup);
          } catch (e) {
            this.$log.warn('could not attach security group to server group:', serverGroup.name, securityGroupId);
            notFoundCaught = true;
          }
        });
      }
    });

    return {notFoundCaught, securityGroups};
  }

  private clearCacheAndRetryAttachingSecurityGroups(application: Application,
                                                    nameBasedSecurityGroups: ISecurityGroup[]): IPromise<any[]> {

    this.infrastructureCaches.clearCache('securityGroups');
    return this.loadSecurityGroups().then((refreshedSecurityGroups: IIndexedSecurityGroups) => {

      application['securityGroupsIndex'] = refreshedSecurityGroups;

      return this.attachSecurityGroups(application, nameBasedSecurityGroups, false);
    });
  }

  private addStackToSecurityGroup(securityGroup: ISecurityGroup): void {
    const nameParts: IComponentName = this.namingService.parseSecurityGroupName(securityGroup.name);
    securityGroup.stack = nameParts.stack;
  }

  private attachSecurityGroups(application: Application,
                               nameBasedSecurityGroups: ISecurityGroup[],
                               retryIfNotFound: boolean): IPromise<any[]> {

    let data: ISecurityGroup[] = [];
    let notFoundCaught = false;
    if (nameBasedSecurityGroups) {
      // reset everything
      application.getDataSource('securityGroups').data = [];
      const nameBasedGroups: ISecurityGroupProcessorResult =
        this.addNameBasedSecurityGroups(application, nameBasedSecurityGroups);
      notFoundCaught = nameBasedGroups.notFoundCaught;
      if (!nameBasedGroups.notFoundCaught) {
        data = nameBasedGroups.securityGroups;
      }
    } else {
      // filter down to empty (name-based only) security groups - we will repopulate usages
      data = application
        .getDataSource('securityGroups')
        .data
        .filter((group: ISecurityGroup) => !group.usages.serverGroups.length && !group.usages.loadBalancers.length);
    }

    if (!notFoundCaught) {

      const loadBalancerSecurityGroups: ISecurityGroupProcessorResult =
        this.addLoadBalancerSecurityGroups(application);
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
      this.$log.warn('Clearing security group cache and trying again...');
      return this.clearCacheAndRetryAttachingSecurityGroups(application, nameBasedSecurityGroups);

    } else {
      data.forEach((sg: ISecurityGroup) => this.addStackToSecurityGroup(sg));
      return this.$q.all(data.map((sg: ISecurityGroup) => this.securityGroupTransformer.normalizeSecurityGroup(sg)))
        .then(() => this.addEntityTags(data));
    }
  }

  private addEntityTags(securityGroups: ISecurityGroup[]): IPromise<ISecurityGroup[]> {
    if (!SETTINGS.feature.entityTags) {
      return this.$q.when(securityGroups);
    }
    const entityIds = securityGroups.map(sg => sg.name);
    return this.entityTagsReader.getAllEntityTags('securitygroup', entityIds).then(tags => {
      securityGroups.forEach(securityGroup => {
        securityGroup.entityTags = tags.find(t => t.entityRef.entityId === securityGroup.name &&
        t.entityRef['account'] === securityGroup.accountName &&
        t.entityRef['region'] === securityGroup.region);
      });
      return securityGroups;
    });
  }

  constructor(private $log: ILogService,
              private $q: IQService,
              private searchService: SearchService,
              private namingService: NamingService,
              private API: Api,
              private infrastructureCaches: InfrastructureCacheService,
              private securityGroupTransformer: SecurityGroupTransformerService,
              private serviceDelegate: any,
              private entityTagsReader: EntityTagsReader) {
    'ngInject';
  }

  public getAllSecurityGroups(): IPromise<IGroupsByAccount[]> {
    return this.API.one('securityGroups').useCache(this.infrastructureCaches.get('securityGroups')).get();
  }

  public getApplicationSecurityGroup(application: Application,
                                     account: string,
                                     region: string,
                                     id: string): IApplicationSecurityGroup {

    let result: IApplicationSecurityGroup = null;
    if (has(application['securityGroupsIndex'], [account, region, id])) {
      result = application['securityGroupsIndex'][account][region][id];
    }

    return result;
  }

  public getApplicationSecurityGroups(application: Application,
                                      nameBasedSecurityGroups: ISecurityGroup[]): IPromise<any> {
    return this.loadSecurityGroups()
      .then((allSecurityGroups: IIndexedSecurityGroups) => {
        application['securityGroupsIndex'] = allSecurityGroups;
      })
      .then(() => this.$q.all([
        application.getDataSource('serverGroups').ready(),
        application.getDataSource('loadBalancers').ready()])
        .then(() => this.attachSecurityGroups(application, nameBasedSecurityGroups, true)));
  }

  public getSecurityGroupDetails(application: Application,
                                 account: string,
                                 provider: string,
                                 region: string,
                                 vpcId: string,
                                 id: string): IPromise<ISecurityGroupDetail> {

    return this.API
      .one('securityGroups')
      .one(account)
      .one(region)
      .one(id)
      .withParams({provider, vpcId})
      .get()
      .then((details: ISecurityGroupDetail) => {

        if (details && details.inboundRules) {
          details.ipRangeRules = details.inboundRules.filter((rule: ISecurityGroupRule & IIPRangeRule) => rule.range);
          details.securityGroupRules = details.inboundRules.filter((rule: ISecurityGroupRule) => rule.securityGroup);
          details.securityGroupRules.forEach((inboundRule: ISecurityGroupRule) => {
            const inboundGroup: ISecurityGroupDetailInboundRule = inboundRule.securityGroup;
            if (!inboundGroup.name) {
              const applicationSecurityGroup: IApplicationSecurityGroup = this.getApplicationSecurityGroup(
                application,
                inboundGroup.accountName,
                details.region,
                inboundGroup.id);
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
          return this.addEntityTags([details]).then(all => all[0]);
        }
        return details;
      });
  }

  public loadSecurityGroups(): IPromise<IIndexedSecurityGroups> {

    return this.getAllSecurityGroups().then((groupsByAccount: IGroupsByAccount[]) => {
      const securityGroups: IReaderSecurityGroup[] = [];
      forOwn(groupsByAccount, (groupsByProvider: IGroupsByProvider, account: string) => {
        return forOwn(groupsByProvider, (groupsByRegion: IRegions, provider: string) => {
          forOwn(groupsByRegion, (groups: IRegionAccount[]) => {
            groups.forEach((group: IRegionAccount) => {
              group.provider = provider;
              group.account = account;
            });
          });
          securityGroups.push({account, provider, securityGroups: groupsByProvider[provider]});
        });
      });

      return SecurityGroupReader.indexSecurityGroups(securityGroups);
    });
  }

  public loadSecurityGroupsByApplicationName(applicationName: string): IPromise<ISecurityGroup[]> {

    return this.searchService.search<ISecurityGroupSearchResult>({
      q: applicationName,
      type: 'securityGroups',
      pageSize: 1000
    }).then((searchResults: ISearchResults<ISecurityGroupSearchResult>) => {

      let result: ISecurityGroup[] = [];
      if (!searchResults || !searchResults.results) {
        this.$log.warn('WARNING: Gate security group endpoint appears to be down.');
      } else {
        result = filter(searchResults.results, {application: applicationName});
      }

      return result;
    });
  }
}

export const SECURITY_GROUP_READER = 'spinnaker.core.securityGroup.read.service';
module(SECURITY_GROUP_READER, [
  SEARCH_SERVICE,
  NAMING_SERVICE,
  INFRASTRUCTURE_CACHE_SERVICE,
  SECURITY_GROUP_TRANSFORMER_SERVICE,
  require('../cloudProvider/serviceDelegate.service.js'),
  API_SERVICE,
  ENTITY_TAGS_READ_SERVICE,
])
  .service('securityGroupReader', SecurityGroupReader);
