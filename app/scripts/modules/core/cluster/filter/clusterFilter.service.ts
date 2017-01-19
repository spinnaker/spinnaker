import {module} from 'angular';
import {debounce, groupBy, forOwn, sortBy, each} from 'lodash';
import {Application} from 'core/application/application.model';
import {ServerGroup} from '../../domain/serverGroup';
import {ICluster} from 'core/domain';
import {Instance} from '../../domain/instance';
import {IEntityTags} from '../../domain/IEntityTags';

interface IParentGrouping {
  subgroups: IClusterSubgroup[] | IServerGroupSubgroup[];
}

export interface IClusterGroup extends IParentGrouping {
  heading: string;
  key: string;
  subgroups: IClusterSubgroup[];
  entityTags?: IEntityTags;
}

export interface IClusterSubgroup extends IParentGrouping {
  heading: string;
  key: string;
  category: string;
  cluster: ICluster;
  subgroups: IServerGroupSubgroup[];
  hasDiscovery?: boolean;
  hasLoadBalancers?: boolean;
  entityTags: IEntityTags;
}

export interface IServerGroupSubgroup {
  heading: string;
  key: string;
  category: string;
  serverGroups: ServerGroup[];
  entityTags: IEntityTags;
}

type Grouping = IClusterGroup | IClusterSubgroup | IServerGroupSubgroup;

export class ClusterFilterService {

  public updateClusterGroups = debounce((application?: Application) => {
    if (!application) {
      application = this.lastApplication;
      if (!this.lastApplication) {
        return null;
      }
    }

    let groups: IClusterGroup[] = [];
    let serverGroups: ServerGroup[] = this.filterServerGroupsForDisplay(application.getDataSource('serverGroups').data);

    const accountGroupings = groupBy(serverGroups, 'account');

    forOwn(accountGroupings, (accountGroup: ServerGroup[], account: string) => {
      const categoryGroupings = groupBy(accountGroup, 'category'),
            clusterGroups: IClusterSubgroup[] = [];

      forOwn(categoryGroupings, (categoryGroup: ServerGroup[], category: string) => {
        const clusterGroupings = groupBy(categoryGroup, 'cluster');

        forOwn(clusterGroupings, (clusterGroup: ServerGroup[], cluster: string) => {
          const regionGroupings = groupBy(clusterGroup, 'region'),
                regionGroups: IServerGroupSubgroup[] = [];

          forOwn(regionGroupings, (regionGroup: ServerGroup[], region: string) => {
            regionGroups.push({
              heading: region,
              category: category,
              serverGroups: regionGroup,
              key: `${region}:${category}`,
              entityTags: (regionGroup[0].clusterEntityTags || []).find(t => t.entityRef['region'] === region),
            });
          });

          const appCluster: ICluster = (application['clusters'] || [])
            .find((c: ICluster) => c.account === account && c.name === cluster && c.category === category);

          if (appCluster) {
            clusterGroups.push({
              heading: cluster,
              category: category,
              key: `${cluster}:${category}`,
              cluster: appCluster,
              subgroups: sortBy(regionGroups, 'heading'),
              entityTags: (clusterGroup[0].clusterEntityTags || []).find(t => t.entityRef['region'] === '*'),
            });
          }
        });
      });

      groups.push({
        heading: account,
        key: account,
        subgroups: sortBy(clusterGroups, ['heading', 'category']),
      });
    });

    this.sortGroupsByHeading(groups);
    this.waypointService.restoreToWaypoint(application.name);
    this.ClusterFilterModel.addTags();
    this.lastApplication = application;
    this.addHealthFlags();
    return groups;
  }, 25);

  private lastApplication: Application;

  private isFilterable: (sortFilter: any) => boolean = this.filterModelService.isFilterable;

  static get $inject() {
    return ['ClusterFilterModel', 'MultiselectModel', 'waypointService', '$log', '$stateParams',
      'filterModelService'];
  }

  public constructor(private ClusterFilterModel: any, private MultiselectModel: any, private waypointService: any,
                     private $log: ng.ILogService, private $stateParams: angular.ui.IStateParamsService,
                     private filterModelService: any) {}

  public clearFilters(): void {
    this.ClusterFilterModel.clearFilters();
    this.ClusterFilterModel.applyParamsToUrl();
  }

  public shouldShowInstance(instance: Instance): boolean {
    if (this.isFilterable(this.ClusterFilterModel.sortFilter.availabilityZone)) {
      const checkedAvailabilityZones: string[] = this.filterModelService.getCheckValues(this.ClusterFilterModel.sortFilter.availabilityZone);
      if (!checkedAvailabilityZones.includes(instance.availabilityZone)) {
        return false;
      }
    }
    if (this.isFilterable(this.ClusterFilterModel.sortFilter.status)) {
      const allCheckedValues: string[] = this.filterModelService.getCheckValues(this.ClusterFilterModel.sortFilter.status);
      const checkedStatus = allCheckedValues.filter(s => s !== 'Disabled');
      if (!checkedStatus.length) {
        return true;
      }
      if (this.ClusterFilterModel.sortFilter.status.Disabled) {
        // filtering should be performed on the server group; always show instances
        return true;
      }
      return checkedStatus.includes(instance.healthState);
    }
    return true;
  }

  public overrideFiltersForUrl(result: any): void {
    if (result.href.includes('/clusters')) {
      this.ClusterFilterModel.clearFilters();
      this.ClusterFilterModel.sortFilter.filter = result.serverGroup ? result.serverGroup :
        result.cluster ? 'cluster:' + result.cluster : '';
      if (result.account) {
        const acct: any = {};
        acct[result.account] = true;
        this.ClusterFilterModel.sortFilter.account = acct;
      }
      if (result.region) {
        const reg: any = {};
        reg[result.region] = true;
        this.ClusterFilterModel.sortFilter.region = reg;
      }
      if (result.stack) {
        const stack: any = {};
        stack[result.stack] = true;
        this.ClusterFilterModel.sortFilter.stack = stack;
      }
      if (result.detail) {
        const detail: any = {};
        detail[result.detail] = true;
        this.ClusterFilterModel.sortFilter.detail = detail;
      }
      if (result.category) {
        const category: any = {};
        category[result.category] = true;
        this.ClusterFilterModel.sortFilter.category = category;
      }
      if (this.$stateParams['application'] === result.application) {
        this.updateClusterGroups();
      }
    }
  }

  private filterServerGroupsForDisplay(serverGroups: ServerGroup[]): ServerGroup[] {
    let filtered: ServerGroup[] = serverGroups.filter(g => this.textFilter(g))
      .filter(g => this.instanceCountFilter(g))
      .filter(g => this.filterModelService.checkAccountFilters(this.ClusterFilterModel)(g))
      .filter(g => this.filterModelService.checkRegionFilters(this.ClusterFilterModel)(g))
      .filter(g => this.filterModelService.checkStackFilters(this.ClusterFilterModel)(g))
      .filter(g => this.filterModelService.checkStatusFilters(this.ClusterFilterModel)(g))
      .filter(g => this.filterModelService.checkProviderFilters(this.ClusterFilterModel)(g))
      .filter(g => this.instanceTypeFilters(g))
      .filter(g => this.instanceFilters(g));

    this.updateMultiselectInstanceGroups(filtered);
    this.updateMultiselectServerGroups(filtered);

    return filtered;
  }

  private updateMultiselectInstanceGroups(serverGroups: ServerGroup[]): void {
    // removes instance groups, selection of instances that are no longer visible;
    // adds new instance ids if selectAll is enabled for an instance group
    if (this.ClusterFilterModel.sortFilter.listInstances && this.ClusterFilterModel.sortFilter.multiselect) {
      let instancesSelected = 0;
      this.MultiselectModel.instanceGroups.forEach((instanceGroup: any) => {
        let match = serverGroups.find((serverGroup) => {
          return serverGroup.name === instanceGroup.serverGroup &&
            serverGroup.region === instanceGroup.region &&
            serverGroup.account === instanceGroup.account &&
            serverGroup.type === instanceGroup.cloudProvider;

        });
        if (!match) {
          // leave it in place, just clear the instanceIds so we retain the selectAll selection if it comes
          // back in subsequent filter operations
          instanceGroup.instanceIds.length = 0;
        } else {
          let filteredInstances = match.instances.filter(i => this.shouldShowInstance(i));
          if (instanceGroup.selectAll) {
            instanceGroup.instanceIds = filteredInstances.map(i => i.id);
          } else {
            instanceGroup.instanceIds = filteredInstances
              .filter((instance) => instanceGroup.instanceIds.includes(instance.id))
              .map((instance) => instance.id);
          }
          instancesSelected += instanceGroup.instanceIds.length;
        }
      });
      this.MultiselectModel.instancesStream.next();
      this.MultiselectModel.syncNavigation();
    } else {
      this.MultiselectModel.instanceGroups.length = 0;
    }
  }

  private updateMultiselectServerGroups(serverGroups: ServerGroup[]): void {
    if (this.ClusterFilterModel.sortFilter.multiselect) {
      if (this.MultiselectModel.serverGroups.length) {
        let remainingKeys = serverGroups.map(s => this.MultiselectModel.makeServerGroupKey(s));
        let toRemove: number[] = [];
        this.MultiselectModel.serverGroups.forEach((group: any, index: number) => {
          if (!remainingKeys.includes(group.key)) {
            toRemove.push(index);
          }
        });
        toRemove.reverse().forEach((index) => this.MultiselectModel.serverGroups.splice(index, 1));
      }
      this.MultiselectModel.serverGroupsStream.next();
      this.MultiselectModel.syncNavigation();
    }

  }

  private instanceTypeFilters(serverGroup: ServerGroup): boolean {
    if (this.isFilterable(this.ClusterFilterModel.sortFilter.instanceType)) {
      let checkedInstanceTypes: string[] = this.filterModelService.getCheckValues(this.ClusterFilterModel.sortFilter.instanceType);
      return checkedInstanceTypes.includes(serverGroup.instanceType);
    } else {
      return true;
    }
  }

  private instanceFilters(serverGroup: ServerGroup): boolean {
    return !this.shouldFilterInstances() || serverGroup.instances.some(i => this.shouldShowInstance(i));
  }

  private shouldFilterInstances(): boolean {
    return this.isFilterable(this.ClusterFilterModel.sortFilter.availabilityZone) ||
      (this.isFilterable(this.ClusterFilterModel.sortFilter.status) &&
      !this.ClusterFilterModel.sortFilter.status.hasOwnProperty('Disabled'));
  }

  private instanceCountFilter(serverGroup: ServerGroup): boolean {
    let shouldInclude = true;
    if (this.ClusterFilterModel.sortFilter.minInstances && !isNaN(this.ClusterFilterModel.sortFilter.minInstances)) {
      shouldInclude = serverGroup.instances.length >= this.ClusterFilterModel.sortFilter.minInstances;
    }
    if (shouldInclude && this.ClusterFilterModel.sortFilter.maxInstances !== null
      && !isNaN(this.ClusterFilterModel.sortFilter.maxInstances)) {
        shouldInclude = serverGroup.instances.length <= this.ClusterFilterModel.sortFilter.maxInstances;
    }
    return shouldInclude;
  }

  private textFilter(serverGroup: ServerGroup): boolean {
    const filter: string = this.ClusterFilterModel.sortFilter.filter.toLowerCase();
    if (!filter) {
      return true;
    }
    if (filter.includes('clusters:')) {
      const clusterNames: string[] = filter.split('clusters:')[1].replace(/\s/g, '').split(',');
      return clusterNames.includes(serverGroup.cluster);
    }

    if (filter.includes('vpc:')) {
      let vpcName: string = filter.split('vpc:')[1];
      return serverGroup.vpcName.toLowerCase() === vpcName.toLowerCase();
    }

    if (filter.includes('tag:')) {
      let match = false;
      let [, tag] = filter.split('tag:');
      let tagKey: string = null;
      let tagValue: string = null;
      if (tag.includes('=')) {
        [tagKey, tagValue] = tag.split('=');
      }
      each(serverGroup.tags || {}, (val: string, key: string) => {
        if (tagKey) {
          if (tagKey.toLowerCase() === key.toLowerCase() && val.toLowerCase().includes(tagValue.toLowerCase())) {
            match = true;
          }
        } else if (val.toLowerCase().includes(tag.toLowerCase())) {
          match = true;
        }
      });
      return match;
    }

    if (filter.includes('detail:')) {
      let detailName: string = filter.split('detail:')[1];
      return serverGroup.detail === detailName.toLowerCase();
    }

    if (filter.includes('cluster:')) {
      let clusterName: string = filter.split('cluster:')[1];
      return serverGroup.cluster === clusterName;
    } else {
      this.addSearchField(serverGroup);
      return filter.split(' ').every((testWord: string) => {
        return serverGroup.searchField.includes(testWord);
      });
    }
  }

  private addSearchField(serverGroup: ServerGroup): void {
    if (serverGroup.searchField) {
      return;
    }
    let buildInfo = '';
    if (serverGroup.buildInfo && serverGroup.buildInfo['jenkins']) {
      buildInfo = [
        '#' + serverGroup.buildInfo['jenkins']['number'],
        serverGroup.buildInfo.jenkins.host,
        serverGroup.buildInfo.jenkins.name].join(' ').toLowerCase();
    }
    if (!serverGroup.searchField) {
      serverGroup.searchField = [
        serverGroup.region.toLowerCase(),
        serverGroup.name.toLowerCase(),
        serverGroup.account.toLowerCase(),
        buildInfo,
        (serverGroup.loadBalancers || []).join(' '),
        (serverGroup.instances || []).map((i: any) => i.id).join(' '),
      ].join(' ');
    }
  }

  private sortGroupsByHeading(groups: IClusterGroup[]): void {
    this.diffSubgroups(this.ClusterFilterModel.groups, groups);
    // sort groups in place so Angular doesn't try to update the world
    this.ClusterFilterModel.groups.sort((a: IClusterGroup, b: IClusterGroup) => a.heading.localeCompare(b.heading));
  }

  private addHealthFlags(): void {
    this.ClusterFilterModel.groups.forEach((group: IClusterGroup) => {
      group.subgroups.forEach((subgroup: IClusterSubgroup) => {
        subgroup.hasDiscovery = subgroup.subgroups.some(g => this.hasDiscovery(g));
        subgroup.hasLoadBalancers = subgroup.subgroups.some(g => this.hasLoadBalancers(g));
      });
    });
  }

  private hasDiscovery(group: IServerGroupSubgroup) {
    return group.serverGroups.some((serverGroup) =>
      (serverGroup.instances || []).some((instance) =>
        (instance.health || []).some((health) => health.type === 'Discovery')
      )
    );
  }

  private hasLoadBalancers(group: IServerGroupSubgroup) {
    return group.serverGroups.some((serverGroup) =>
      (serverGroup.instances || []).some((instance) =>
        (instance.health || []).some((health) => health.type === 'LoadBalancer')
      )
    );
  }

  private diffSubgroups(oldGroups: Grouping[], newGroups: Grouping[]): void {
    const groupsToRemove: number[] = [];
    oldGroups.forEach((oldGroup: Grouping, idx: number) => {
      let newGroup = (newGroups || []).find(group => group.key === oldGroup.key);
      if (!newGroup) {
        groupsToRemove.push(idx);
      } else {
        // Not crazy about this, but the alternative is to have four diffing methods doing basically the same thing,
        // just with very slightly different method signatures
        if (newGroup.hasOwnProperty('subgroups')) {
          this.diffSubgroups((oldGroup as IParentGrouping).subgroups, (newGroup as IParentGrouping).subgroups);
        }
        if (newGroup.hasOwnProperty('cluster')) {
          (oldGroup as IClusterSubgroup).cluster = (newGroup as IClusterSubgroup).cluster;
          this.diffSubgroups((oldGroup as IParentGrouping).subgroups, (newGroup as IParentGrouping).subgroups);
        }
        if (newGroup.hasOwnProperty('serverGroups')) {
          this.diffServerGroups(oldGroup as IServerGroupSubgroup, newGroup as IServerGroupSubgroup);
        }
        if (oldGroup.entityTags || newGroup.entityTags) {
          oldGroup.entityTags = newGroup.entityTags;
        }
      }
    });
    groupsToRemove.reverse().forEach((idx: number) => {
      oldGroups.splice(idx, 1);
    });
    newGroups.forEach((newGroup: Grouping) => {
      const match: Grouping = oldGroups.find(g => g.key === newGroup.key);
      if (!match) {
        oldGroups.push(newGroup);
      }
    });
  }

  private diffServerGroups(oldGroup: IServerGroupSubgroup, newGroup: IServerGroupSubgroup): void {
    if (oldGroup.category !== newGroup.category) {
      return;
    }

    const toRemove: number[] = [];
    oldGroup.serverGroups.forEach((serverGroup: ServerGroup, idx: number) => {
      const newServerGroup: ServerGroup = newGroup.serverGroups.find(g => g.name === serverGroup.name &&
        g.account === serverGroup.account && g.region === serverGroup.region);

      if (!newServerGroup) {
        this.$log.debug('server group no longer found, removing:', serverGroup.name, serverGroup.account, serverGroup.region, serverGroup.category);
        toRemove.push(idx);
      } else {
        if (serverGroup.stringVal !== newServerGroup.stringVal) {
          this.$log.debug('change detected, updating server group:', serverGroup.name, serverGroup.account, serverGroup.region, serverGroup.category);
          oldGroup.serverGroups[idx] = newServerGroup;
        }
        if (serverGroup.runningExecutions || newServerGroup.runningExecutions) {
          serverGroup.runningExecutions = newServerGroup.runningExecutions;
        }
        if (serverGroup.runningTasks || newServerGroup.runningTasks) {
          serverGroup.runningTasks = newServerGroup.runningTasks;
        }
      }
    });
    toRemove.reverse().forEach((idx) => {
      oldGroup.serverGroups.splice(idx, 1);
    });

    newGroup.serverGroups.forEach((serverGroup) => {
      const oldServerGroup: ServerGroup = oldGroup.serverGroups.find(g => g.name === serverGroup.name &&
        g.account === serverGroup.account && g.region === serverGroup.region);

      if (!oldServerGroup) {
        this.$log.debug('new server group found, adding', serverGroup.name, serverGroup.account, serverGroup.region);
        oldGroup.serverGroups.push(serverGroup);
      }
    });
  }

}

export const CLUSTER_FILTER_SERVICE = 'spinnaker.core.cluster.filter.service';
module(CLUSTER_FILTER_SERVICE, [
  require('angular-ui-router'),
  require('./clusterFilter.model'),
  require('./multiselect.model'),
  require('../../utils/waypoints/waypoint.service'),
  require('../../filterModel/filter.model.service'),
]).service('clusterFilterService', ClusterFilterService);
