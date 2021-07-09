import { each, every, forOwn, groupBy, isEmpty, some, sortBy } from 'lodash';
import { Debounce } from 'lodash-decorators';
import { $log } from 'ngimport';
import { Subject } from 'rxjs';

import { Application } from '../../application/application.model';
import { ICluster, IEntityTags, IInstance, IManagedResourceSummary, IServerGroup } from '../../domain';
import { FilterModelService, ISortFilter } from '../../filterModel';
import { ILabelFilter, trueKeyObjectToLabelFilters } from './labelFilterUtils';
import { ReactInjector } from '../../reactShims';
import { ClusterState } from '../../state';

export interface IParentGrouping {
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
  isManaged: boolean;
  managedResourceSummary?: IManagedResourceSummary;
}

export interface IServerGroupSubgroup {
  heading: string;
  key: string;
  category: string;
  serverGroups: IServerGroup[];
  entityTags: IEntityTags;
  isManaged: boolean;
  managedResourceSummary?: IManagedResourceSummary;
}

export type Grouping = IClusterGroup | IClusterSubgroup | IServerGroupSubgroup;

export class ClusterFilterService {
  public groupsUpdatedStream: Subject<IClusterGroup[]> = new Subject<IClusterGroup[]>();

  private lastApplication: Application;

  private isFilterable: (sortFilter: any) => boolean = FilterModelService.isFilterable;

  @Debounce(25)
  public updateClusterGroups(application?: Application): void {
    if (!application) {
      application = this.lastApplication;
      if (!this.lastApplication) {
        return null;
      }
    }

    const groups: IClusterGroup[] = [];
    const serverGroups: IServerGroup[] = this.filterServerGroupsForDisplay(
      application.getDataSource('serverGroups').data,
    );

    const accountGroupings = groupBy(serverGroups, 'account');

    forOwn(accountGroupings, (accountGroup: IServerGroup[], account: string) => {
      const categoryGroupings = groupBy(accountGroup, 'category');
      const clusterGroups: IClusterSubgroup[] = [];

      forOwn(categoryGroupings, (categoryGroup: IServerGroup[], category: string) => {
        const clusterGroupings = groupBy(categoryGroup, 'cluster');

        forOwn(clusterGroupings, (clusterGroup: IServerGroup[], cluster: string) => {
          const regionGroupings = groupBy(clusterGroup, 'region');
          const regionGroups: IServerGroupSubgroup[] = [];

          forOwn(regionGroupings, (regionGroup: IServerGroup[], region: string) => {
            regionGroups.push({
              heading: region,
              category,
              serverGroups: regionGroup,
              key: `${region}:${category}`,
              entityTags: (regionGroup[0].clusterEntityTags || []).find((t) => t.entityRef['region'] === region),
              isManaged: !!regionGroup[0].isManaged,
              managedResourceSummary: regionGroup[0].managedResourceSummary,
            });
          });

          const appCluster: ICluster = (application.clusters || []).find(
            (c: ICluster) => c.account === account && c.name === cluster && c.category === category,
          );

          if (appCluster) {
            const isEntireClusterManaged = regionGroups.every(({ isManaged }) => isManaged);
            clusterGroups.push({
              heading: cluster,
              category,
              key: `${cluster}:${category}`,
              cluster: appCluster,
              subgroups: sortBy(regionGroups, 'heading'),
              entityTags: (clusterGroup[0].clusterEntityTags || []).find(
                (t) => t.entityRef['region'] === '*' || t.entityRef['region'] === undefined,
              ),
              isManaged: isEntireClusterManaged,
              managedResourceSummary: isEntireClusterManaged ? regionGroups[0].managedResourceSummary : undefined,
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
    ClusterState.filterModel.asFilterModel.addTags();
    this.lastApplication = application;
    this.addHealthFlags();
    this.groupsUpdatedStream.next(ClusterState.filterModel.asFilterModel.groups);
  }

  public clearFilters(): void {
    ClusterState.filterModel.asFilterModel.clearFilters();
    ClusterState.filterModel.asFilterModel.applyParamsToUrl();
  }

  public shouldShowInstance(instance: IInstance): boolean {
    const sortFilter: ISortFilter = ClusterState.filterModel.asFilterModel.sortFilter;
    if (this.isFilterable(sortFilter.availabilityZone)) {
      const checkedAvailabilityZones: string[] = FilterModelService.getCheckValues(sortFilter.availabilityZone);
      if (!checkedAvailabilityZones.includes(instance.availabilityZone)) {
        return false;
      }
    }
    if (this.isFilterable(sortFilter.status)) {
      const allCheckedValues: string[] = FilterModelService.getCheckValues(sortFilter.status);
      const checkedStatus = allCheckedValues.filter((s) => s !== 'Disabled');
      if (!checkedStatus.length) {
        return true;
      }
      if (sortFilter.status.Disabled) {
        // filtering should be performed on the server group; always show instances
        return true;
      }
      return checkedStatus.includes(instance.healthState);
    }
    return true;
  }

  public overrideFiltersForUrl(result: any): void {
    if (result.href.includes('/clusters')) {
      ClusterState.filterModel.asFilterModel.clearFilters();
      const sortFilter: ISortFilter = ClusterState.filterModel.asFilterModel.sortFilter;
      sortFilter.filter = result.serverGroup ? result.serverGroup : result.cluster ? 'cluster:' + result.cluster : '';
      if (result.account) {
        const acct: any = {};
        acct[result.account] = true;
        sortFilter.account = acct;
      }
      if (result.region) {
        const reg: any = {};
        reg[result.region] = true;
        sortFilter.region = reg;
      }
      if (result.stack) {
        const stack: any = {};
        stack[result.stack] = true;
        sortFilter.stack = stack;
      }
      if (result.detail) {
        const detail: any = {};
        detail[result.detail] = true;
        sortFilter.detail = detail;
      }
      if (result.category) {
        const category: any = {};
        category[result.category] = true;
        sortFilter.category = category;
      }
      if (ReactInjector.$stateParams.application === result.application) {
        this.updateClusterGroups();
      }
    }
  }

  private filterServerGroupsForDisplay(serverGroups: IServerGroup[]): IServerGroup[] {
    const filtered: IServerGroup[] = serverGroups
      .filter((g) => this.textFilter(g))
      .filter((g) => this.instanceCountFilter(g))
      .filter((g) => FilterModelService.checkAccountFilters(ClusterState.filterModel.asFilterModel)(g))
      .filter((g) => FilterModelService.checkRegionFilters(ClusterState.filterModel.asFilterModel)(g))
      .filter((g) => FilterModelService.checkStackFilters(ClusterState.filterModel.asFilterModel)(g))
      .filter((g) => FilterModelService.checkDetailFilters(ClusterState.filterModel.asFilterModel)(g))
      .filter((g) => FilterModelService.checkStatusFilters(ClusterState.filterModel.asFilterModel)(g))
      .filter((g) => FilterModelService.checkProviderFilters(ClusterState.filterModel.asFilterModel)(g))
      .filter((g) => this.instanceTypeFilters(g))
      .filter((g) => this.instanceFilters(g))
      .filter((g) => this.labelFilters(g));

    this.updateMultiselectInstanceGroups(filtered);
    this.updateMultiselectServerGroups(filtered);

    return filtered;
  }

  private updateMultiselectInstanceGroups(serverGroups: IServerGroup[]): void {
    // removes instance groups, selection of instances that are no longer visible;
    // adds new instance ids if selectAll is enabled for an instance group
    const sortFilter: ISortFilter = ClusterState.filterModel.asFilterModel.sortFilter;
    if (sortFilter.listInstances && sortFilter.multiselect) {
      ClusterState.multiselectModel.instanceGroups.forEach((instanceGroup: any) => {
        const match = serverGroups.find((serverGroup) => {
          return (
            serverGroup.name === instanceGroup.serverGroup &&
            serverGroup.region === instanceGroup.region &&
            serverGroup.account === instanceGroup.account &&
            serverGroup.type === instanceGroup.cloudProvider
          );
        });
        if (!match) {
          // leave it in place, just clear the instanceIds so we retain the selectAll selection if it comes
          // back in subsequent filter operations
          instanceGroup.instanceIds.length = 0;
        } else {
          const filteredInstances = match.instances.filter((i) => this.shouldShowInstance(i));
          if (instanceGroup.selectAll) {
            instanceGroup.instanceIds = filteredInstances.map((i) => i.id);
          } else {
            instanceGroup.instanceIds = filteredInstances
              .filter((instance) => instanceGroup.instanceIds.includes(instance.id))
              .map((instance) => instance.id);
          }
        }
      });
      ClusterState.multiselectModel.instancesStream.next();
      ClusterState.multiselectModel.syncNavigation();
    } else {
      ClusterState.multiselectModel.instanceGroups.length = 0;
    }
  }

  private updateMultiselectServerGroups(serverGroups: IServerGroup[]): void {
    if (ClusterState.filterModel.asFilterModel.sortFilter.multiselect) {
      if (ClusterState.multiselectModel.serverGroups.length) {
        const remainingKeys = serverGroups.map((s) => ClusterState.multiselectModel.makeServerGroupKey(s));
        const toRemove: number[] = [];
        ClusterState.multiselectModel.serverGroups.forEach((group: any, index: number) => {
          if (!remainingKeys.includes(group.key)) {
            toRemove.push(index);
          }
        });
        toRemove.reverse().forEach((index) => ClusterState.multiselectModel.serverGroups.splice(index, 1));
      }
      ClusterState.multiselectModel.serverGroupsStream.next();
      ClusterState.multiselectModel.syncNavigation();
    }
  }

  private instanceTypeFilters(serverGroup: IServerGroup): boolean {
    const sortFilter: ISortFilter = ClusterState.filterModel.asFilterModel.sortFilter;
    if (this.isFilterable(sortFilter.instanceType)) {
      const checkedInstanceTypes: string[] = FilterModelService.getCheckValues(sortFilter.instanceType);
      return checkedInstanceTypes.includes(serverGroup.instanceType);
    } else {
      return true;
    }
  }

  private instanceFilters(serverGroup: IServerGroup): boolean {
    return !this.shouldFilterInstances() || serverGroup.instances.some((i) => this.shouldShowInstance(i));
  }

  private shouldFilterInstances(): boolean {
    const sortFilter: ISortFilter = ClusterState.filterModel.asFilterModel.sortFilter;
    return (
      this.isFilterable(sortFilter.availabilityZone) ||
      (this.isFilterable(sortFilter.status) && !sortFilter.status.hasOwnProperty('Disabled'))
    );
  }

  private instanceCountFilter(serverGroup: IServerGroup): boolean {
    let shouldInclude = true;
    const sortFilter: ISortFilter = ClusterState.filterModel.asFilterModel.sortFilter;
    if (sortFilter.minInstances && !isNaN(sortFilter.minInstances)) {
      shouldInclude = serverGroup.instances.length >= sortFilter.minInstances;
    }
    if (shouldInclude && sortFilter.maxInstances !== null && !isNaN(sortFilter.maxInstances)) {
      shouldInclude = serverGroup.instances.length <= sortFilter.maxInstances;
    }
    return shouldInclude;
  }

  private labelFilters(serverGroup: IServerGroup): boolean {
    const labelFiltersAsTrueKeyObj = ClusterState.filterModel.asFilterModel.sortFilter.labels;
    const labelFilters: ILabelFilter[] = trueKeyObjectToLabelFilters(labelFiltersAsTrueKeyObj);
    if (isEmpty(labelFilters)) {
      return true;
    }
    return every(labelFilters, ({ key: filterKey, value: filterValue }) => {
      if (filterKey === null || filterValue === null) {
        return true;
      }
      return some(serverGroup.labels || {}, (value, key) => {
        const keyMatch = filterKey && filterKey === key;
        const valueMatch = filterValue && filterValue === value;
        return keyMatch && valueMatch;
      });
    });
  }

  private textFilter(serverGroup: IServerGroup): boolean {
    const filter: string = ClusterState.filterModel.asFilterModel.sortFilter.filter.toLowerCase();
    if (!filter) {
      return true;
    }
    if (filter.includes('clusters:')) {
      const clusterNames: string[] = filter.split('clusters:')[1].replace(/\s/g, '').split(',');
      return clusterNames.includes(serverGroup.cluster);
    }

    if (filter.includes('vpc:')) {
      const vpcName: string = filter.split('vpc:')[1];
      return serverGroup.vpcName.toLowerCase() === vpcName.toLowerCase();
    }

    if (filter.includes('tag:')) {
      let match = false;
      const [, tag] = filter.split('tag:');
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
      const detailName: string = filter.split('detail:')[1];
      return serverGroup.detail === detailName.toLowerCase();
    }

    if (filter.includes('cluster:')) {
      const clusterName: string = filter.split('cluster:')[1];
      return serverGroup.cluster === clusterName;
    }

    if (filter.includes('labels:')) {
      const caseSensitiveFilter: string = ClusterState.filterModel.asFilterModel.sortFilter.filter;
      const labelsStr = caseSensitiveFilter.split('labels:')[1];
      const labels = labelsStr.split(',').map((l) => l.trim());

      return every(labels, (label) => {
        let labelKey: string;
        let labelValue: string;
        if (label.includes('=')) {
          [labelKey, labelValue] = label.split('=');
        }
        if (!labelKey || !labelValue) {
          return false;
        }
        return some(serverGroup.labels || {}, (val: string, key: string) => {
          return labelKey === key && val === labelValue;
        });
      });
    } else {
      this.addSearchField(serverGroup);
      return filter.split(' ').every((testWord: string) => {
        return serverGroup.searchField.includes(testWord);
      });
    }
  }

  private addSearchField(serverGroup: IServerGroup): void {
    if (serverGroup.searchField) {
      return;
    }
    let buildInfo = '';
    if (serverGroup.buildInfo && serverGroup.buildInfo['jenkins']) {
      buildInfo = [
        '#' + serverGroup.buildInfo['jenkins']['number'],
        serverGroup.buildInfo.jenkins.host,
        serverGroup.buildInfo.jenkins.name,
      ]
        .join(' ')
        .toLowerCase();
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
    this.diffSubgroups(ClusterState.filterModel.asFilterModel.groups, groups);
    // sort groups in place so Angular doesn't try to update the world
    ClusterState.filterModel.asFilterModel.groups.sort((a: IClusterGroup, b: IClusterGroup) =>
      a.heading.localeCompare(b.heading),
    );
  }

  private addHealthFlags(): void {
    ClusterState.filterModel.asFilterModel.groups.forEach((group: IClusterGroup) => {
      group.subgroups.forEach((subgroup: IClusterSubgroup) => {
        subgroup.hasDiscovery = subgroup.subgroups.some((g) => this.hasDiscovery(g));
        subgroup.hasLoadBalancers = subgroup.subgroups.some((g) => this.hasLoadBalancers(g));
      });
    });
  }

  private hasDiscovery(group: IServerGroupSubgroup) {
    return group.serverGroups.some((serverGroup) =>
      (serverGroup.instances || []).some((instance) =>
        (instance.health || []).some((health) => health.type === 'Discovery'),
      ),
    );
  }

  private hasLoadBalancers(group: IServerGroupSubgroup) {
    return group.serverGroups.some((serverGroup) =>
      (serverGroup.instances || []).some((instance) =>
        (instance.health || []).some((health) => health.type === 'LoadBalancer'),
      ),
    );
  }

  private diffSubgroups(oldGroups: Grouping[], newGroups: Grouping[]): void {
    const groupsToRemove: number[] = [];
    oldGroups.forEach((oldGroup: Grouping, idx: number) => {
      const newGroup = (newGroups || []).find((group) => group.key === oldGroup.key);
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
        if (oldGroup.hasOwnProperty('isManaged') || newGroup.hasOwnProperty('isManaged')) {
          const oldSubGroup = oldGroup as IClusterSubgroup | IServerGroupSubgroup;
          const newSubGroup = newGroup as IClusterSubgroup | IServerGroupSubgroup;
          oldSubGroup.isManaged = newSubGroup.isManaged;
          oldSubGroup.managedResourceSummary = newSubGroup.managedResourceSummary;
        }
      }
    });
    groupsToRemove.reverse().forEach((idx: number) => {
      oldGroups.splice(idx, 1);
    });
    newGroups.forEach((newGroup: Grouping) => {
      const match: Grouping = oldGroups.find((g) => g.key === newGroup.key);
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
    oldGroup.serverGroups.forEach((serverGroup: IServerGroup, idx: number) => {
      const newServerGroup: IServerGroup = newGroup.serverGroups.find(
        (g) => g.name === serverGroup.name && g.account === serverGroup.account && g.region === serverGroup.region,
      );

      if (!newServerGroup) {
        $log.debug(
          'server group no longer found, removing:',
          serverGroup.name,
          serverGroup.account,
          serverGroup.region,
          serverGroup.category,
        );
        toRemove.push(idx);
      } else {
        if (serverGroup.stringVal !== newServerGroup.stringVal) {
          $log.debug(
            'change detected, updating server group:',
            serverGroup.name,
            serverGroup.account,
            serverGroup.region,
            serverGroup.category,
          );
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
      const oldServerGroup: IServerGroup = oldGroup.serverGroups.find(
        (g) => g.name === serverGroup.name && g.account === serverGroup.account && g.region === serverGroup.region,
      );

      if (!oldServerGroup) {
        $log.debug('new server group found, adding', serverGroup.name, serverGroup.account, serverGroup.region);
        oldGroup.serverGroups.push(serverGroup);
      }
    });
  }
}
