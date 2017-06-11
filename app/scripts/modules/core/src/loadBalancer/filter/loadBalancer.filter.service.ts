import { ILogService, module } from 'angular';
import { chain, find, forOwn, groupBy, includes, intersection, map, some, sortBy, values, without } from 'lodash';
import { Debounce } from 'lodash-decorators';
import { Subject } from 'rxjs';
import autoBindMethods from 'class-autobind-decorator';

import { Application } from 'core/application/application.model';
import { ILoadBalancer, ILoadBalancerGroup, IInstance, IServerGroup } from 'core/domain';
import { LOAD_BALANCER_FILTER_MODEL, LoadBalancerFilterModel } from './loadBalancerFilter.model';

@autoBindMethods
export class LoadBalancerFilterService {

  public groupsUpdatedStream: Subject<ILoadBalancerGroup[]> = new Subject<ILoadBalancerGroup[]>();

  private isFilterable: (object: any) => boolean;
  private getCheckValues: (object: any) => string[];
  private lastApplication: Application;

  constructor(private loadBalancerFilterModel: LoadBalancerFilterModel,
              private filterModelService: any,
              private $log: ILogService) {
    'ngInject';
    this.isFilterable = filterModelService.isFilterable;
    this.getCheckValues = filterModelService.getCheckValues;

  }

  private addSearchFields(loadBalancer: ILoadBalancer): void {
    if (!loadBalancer.searchField) {
      loadBalancer.searchField = [
        loadBalancer.name,
        loadBalancer.region.toLowerCase(),
        loadBalancer.account,
        map(loadBalancer.serverGroups, 'name').join(' '),
        map(loadBalancer.instances, 'id').join(' '),
      ].join(' ');
    }
  }

  private checkSearchTextFilter(loadBalancer: ILoadBalancer): boolean {
    const filter = this.loadBalancerFilterModel.asFilterModel.sortFilter.filter;
    if (!filter) {
      return true;
    }

    if (filter.includes('vpc:')) {
      const [, vpcName] = /vpc:([\w-]*)/.exec(filter);
      return loadBalancer.vpcName.toLowerCase() === vpcName.toLowerCase();
    }
    this.addSearchFields(loadBalancer);
    return filter.split(' ').every((testWord: string) => {
      return loadBalancer.searchField.includes(testWord);
    });
  }

  public filterLoadBalancersForDisplay(loadBalancers: ILoadBalancer[]): ILoadBalancer[] {
    return chain(loadBalancers)
      .filter((lb) => this.checkSearchTextFilter(lb))
      .filter((lb) => this.filterModelService.checkAccountFilters(this.loadBalancerFilterModel)(lb))
      .filter((lb) => this.filterModelService.checkRegionFilters(this.loadBalancerFilterModel)(lb))
      .filter((lb) => this.filterModelService.checkStackFilters(this.loadBalancerFilterModel)(lb))
      .filter((lb) => this.filterModelService.checkStatusFilters(this.loadBalancerFilterModel)(lb))
      .filter((lb) => this.filterModelService.checkProviderFilters(this.loadBalancerFilterModel)(lb))
      .filter((lb) => this.instanceFilters(lb))
      .value();
  }

  private instanceFilters(loadBalancer: ILoadBalancer): boolean {
    return !this.shouldFilterInstances() || some(loadBalancer.instances, (instance) => this.shouldShowInstance(instance));
  }

  private shouldFilterInstances(): boolean {
    return this.isFilterable(this.loadBalancerFilterModel.asFilterModel.sortFilter.status) ||
      this.isFilterable(this.loadBalancerFilterModel.asFilterModel.sortFilter.availabilityZone);
  }

  public shouldShowInstance(instance: IInstance): boolean {
    if (this.isFilterable(this.loadBalancerFilterModel.asFilterModel.sortFilter.availabilityZone)) {
      const checkedAvailabilityZones = this.getCheckValues(this.loadBalancerFilterModel.asFilterModel.sortFilter.availabilityZone);
      if (!checkedAvailabilityZones.includes(instance.zone)) {
        return false;
      }
    }
    if (this.isFilterable(this.loadBalancerFilterModel.asFilterModel.sortFilter.status)) {
      const allCheckedValues = this.getCheckValues(this.loadBalancerFilterModel.asFilterModel.sortFilter.status);
      const checkedStatus = without(allCheckedValues, 'Disabled');
      if (!checkedStatus.length) {
        return true;
      }
      return includes(checkedStatus, instance.healthState);
    }
    return true;
  }

  private diffSubgroups(oldGroups: ILoadBalancerGroup[], newGroups: ILoadBalancerGroup[]): void {
    const groupsToRemove: number[] = [];

    oldGroups.forEach((oldGroup, idx) => {
      const newGroup = find(newGroups, { heading: oldGroup.heading });
      if (!newGroup) {
        groupsToRemove.push(idx);
      } else {
        if (newGroup.loadBalancer) {
          oldGroup.loadBalancer = newGroup.loadBalancer;
        }
        if (newGroup.serverGroups) {
          this.diffServerGroups(oldGroup, newGroup);
        }
        if (newGroup.subgroups) {
          this.diffSubgroups(oldGroup.subgroups, newGroup.subgroups);
        }
      }
    });
    groupsToRemove.reverse().forEach((idx) => {
      oldGroups.splice(idx, 1);
    });
    newGroups.forEach((newGroup) => {
      const match = find(oldGroups, { heading: newGroup.heading });
      if (!match) {
        oldGroups.push(newGroup);
      }
    });
  }

  private diffServerGroups(oldGroup: ILoadBalancerGroup, newGroup: ILoadBalancerGroup): void {
    const toRemove: number[] = [];
    oldGroup.serverGroups.forEach((serverGroup, idx) => {
      serverGroup.stringVal = serverGroup.stringVal || JSON.stringify(serverGroup, this.jsonReplacer);
      const newServerGroup = find(newGroup.serverGroups, { name: serverGroup.name, account: serverGroup.account, region: serverGroup.region });
      if (!newServerGroup) {
        this.$log.debug('server group no longer found, removing:', serverGroup.name, serverGroup.account, serverGroup.region);
        toRemove.push(idx);
      } else {
        newServerGroup.stringVal = newServerGroup.stringVal || JSON.stringify(newServerGroup, this.jsonReplacer);
        if (serverGroup.stringVal !== newServerGroup.stringVal) {
          this.$log.debug('change detected, updating server group:', serverGroup.name, serverGroup.account, serverGroup.region);
          oldGroup.serverGroups[idx] = newServerGroup;
        }
      }
    });
    toRemove.reverse().forEach((idx) => {
      oldGroup.serverGroups.splice(idx, 1);
    });
    newGroup.serverGroups.forEach((serverGroup) => {
      const oldServerGroup = find(oldGroup.serverGroups, { name: serverGroup.name, account: serverGroup.account, region: serverGroup.region });
      if (!oldServerGroup) {
        this.$log.debug('new server group found, adding', serverGroup.name, serverGroup.account, serverGroup.region);
        oldGroup.serverGroups.push(serverGroup);
      }
    });
  }

  public sortGroupsByHeading(groups: ILoadBalancerGroup[]): void {
    this.diffSubgroups(this.loadBalancerFilterModel.asFilterModel.groups, groups);

    // sort groups in place so Angular doesn't try to update the world
    this.loadBalancerFilterModel.asFilterModel.groups.sort((a, b) => {
      if (a.heading < b.heading) {
        return -1;
      }
      if (a.heading > b.heading) {
        return 1;
      }
      return 0;
    });
  }

  public clearFilters(): void {
    this.loadBalancerFilterModel.asFilterModel.clearFilters();
    this.loadBalancerFilterModel.asFilterModel.applyParamsToUrl();
  }

  private filterServerGroups(loadBalancer: ILoadBalancer): IServerGroup[] {
    if (this.shouldFilterInstances()) {
      return loadBalancer.serverGroups.filter((serverGroup) => {
        return serverGroup.instances.some((instance: IInstance) => this.shouldShowInstance(instance));
      });
    }
    return loadBalancer.serverGroups;
  }

  private jsonReplacer(key: any, val: any): string {
    if (typeof key === 'string' && key.charAt(0) === '$' && key.charAt(1) === '$') {
      val = undefined;
    }
    return val;
  }

  @Debounce(25)
  public updateLoadBalancerGroups(application: Application): void {
    if (!application) {
      application = this.lastApplication;
      if (!this.lastApplication) {
        return null;
      }
    }

    const groups: ILoadBalancerGroup[] = [];
    const loadBalancers = this.filterLoadBalancersForDisplay(application.loadBalancers.data);
    const grouped = groupBy(loadBalancers, 'account');

    forOwn(grouped, (group, account) => {
      const groupedByType = values(groupBy(group, 'loadBalancerType'));
      const namesByType = groupedByType.map((g) => g.map((lb) => lb.name));
      const crossTypeLoadBalancerNames = namesByType.length > 1 ? intersection(...namesByType).reduce<{[key: string]: boolean}>((acc, name) => {
        acc[name] = true;
        return acc;
      }, {}) : {};
      const subGroupings = groupBy(group, (lb) => `${lb.name}:${lb.loadBalancerType}`),
            subGroups: ILoadBalancerGroup[] = [];

      forOwn(subGroupings, (subGroup, nameAndType) => {
        const [ name, type ] = nameAndType.split(':');
        const subSubGroups: ILoadBalancerGroup[] = [];
        subGroup.forEach((loadBalancer) => {
          subSubGroups.push({
            heading: loadBalancer.region,
            loadBalancer: loadBalancer,
            serverGroups: this.filterServerGroups(loadBalancer),
          });
        });

        const heading = `${name}${crossTypeLoadBalancerNames[name] && type ? ` (${type})` : ''}`;
        subGroups.push({
          heading,
          subgroups: sortBy(subSubGroups, 'heading'),
        });
      });

      groups.push( { heading: account, subgroups: sortBy(subGroups, 'heading') } );

    });

    this.sortGroupsByHeading(groups);
    this.loadBalancerFilterModel.asFilterModel.addTags();
    this.lastApplication = application;
    this.groupsUpdatedStream.next(groups);
  };
}

export const LOAD_BALANCER_FILTER_SERVICE = 'spinnaker.core.loadBalancer.filter.service';
module(LOAD_BALANCER_FILTER_SERVICE, [
  LOAD_BALANCER_FILTER_MODEL,
  require('core/filterModel/filter.model.service.js')
]).factory('loadBalancerFilterService', (loadBalancerFilterModel: LoadBalancerFilterModel, filterModelService: any, $log: ILogService) =>
                                        new LoadBalancerFilterService(loadBalancerFilterModel, filterModelService, $log));
