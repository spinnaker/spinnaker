import { Subject } from 'rxjs';

import { IServerGroup } from '../../domain';
import { IMultiInstanceGroup } from '../../instance/instance.write.service';
import { IMoniker } from '../../naming';
import { ReactInjector } from '../../reactShims';
import { ClusterState } from '../../state';

export interface IMultiselectServerGroup {
  key: string;
  account: string;
  region: string;
  provider: string;
  name: string;
  moniker: IMoniker;
}

export class MultiselectModel {
  public instanceGroups: IMultiInstanceGroup[];
  public instancesStream: Subject<void>;
  public serverGroups: IMultiselectServerGroup[];
  public serverGroupsStream: Subject<void>;

  constructor() {
    this.instanceGroups = [];
    this.instancesStream = new Subject();

    this.serverGroups = [];
    this.serverGroupsStream = new Subject();
  }

  public syncNavigation(): void {
    const { $state } = ReactInjector;
    if (!ClusterState.filterModel.asFilterModel.sortFilter.multiselect && $state.params.multiselect) {
      $state.go('.', { multiselect: null }, { inherit: true });
    }
    if (ClusterState.filterModel.asFilterModel.sortFilter.multiselect && !$state.params.multiselect) {
      $state.go('.', { multiselect: true }, { inherit: true });
    }

    if ($state.includes('**.multipleInstances') && !ClusterState.filterModel.asFilterModel.sortFilter.multiselect) {
      this.deselectAllInstances();
      $state.go('^');
      return;
    }

    if ($state.includes('**.multipleServerGroups') && !ClusterState.filterModel.asFilterModel.sortFilter.multiselect) {
      this.clearAllServerGroups();
      $state.go('^');
      return;
    }

    const instancesSelected = this.instanceGroups.reduce((acc, group) => acc + group.instanceIds.length, 0);

    if ($state.includes('**.multipleInstances') && !instancesSelected) {
      $state.go('^');
    }
    if (!$state.includes('**.multipleInstances') && instancesSelected) {
      if (this.isClusterChildState()) {
        // from a child state, e.g. instanceDetails
        $state.go('^.multipleInstances');
      } else {
        $state.go('.multipleInstances');
      }
    }
    if ($state.includes('**.multipleServerGroups') && !this.serverGroups.length) {
      $state.go('^');
      return;
    }
    if (!$state.includes('**.multipleServerGroups') && this.serverGroups.length) {
      if (this.isClusterChildState()) {
        $state.go('^.multipleServerGroups');
      } else {
        $state.go('.multipleServerGroups');
      }
    }
  }

  public deselectAllInstances(): void {
    this.instanceGroups.forEach((instanceGroup) => {
      instanceGroup.instanceIds.length = 0;
      instanceGroup.selectAll = false;
    });
  }

  public clearAllInstanceGroups(): void {
    this.instanceGroups.length = 0;
    this.instancesStream.next();
  }

  public clearAllServerGroups(): void {
    this.serverGroups.length = 0;
    this.serverGroupsStream.next();
  }

  public clearAll(): void {
    this.clearAllInstanceGroups();
    this.clearAllServerGroups();
  }

  public getOrCreateInstanceGroup(serverGroup: IServerGroup): IMultiInstanceGroup {
    const serverGroupName = serverGroup.name;
    const account = serverGroup.account;
    const region = serverGroup.region;
    const cloudProvider = serverGroup.type;
    let result = this.instanceGroups.find(
      (instanceGroup) =>
        instanceGroup.serverGroup === serverGroupName &&
        instanceGroup.account === account &&
        instanceGroup.region === region &&
        instanceGroup.cloudProvider === cloudProvider,
    );
    if (!result) {
      // when creating a new group, include an instance ID if we're deep-linked into the details view
      const params = ReactInjector.$state.params;
      const instanceIds = (serverGroup.instances || [])
        .filter((instance) => instance.provider === params.provider && instance.id === params.instanceId)
        .map((instance) => instance.id);
      result = {
        serverGroup: serverGroupName,
        account,
        region,
        cloudProvider,
        instanceIds,
        instances: [], // populated by details controller
        selectAll: false,
      };
      this.instanceGroups.push(result);
    }
    return result;
  }

  public makeServerGroupKey(serverGroup: IServerGroup): string {
    return [serverGroup.type, serverGroup.account, serverGroup.region, serverGroup.name, serverGroup.category].join(
      ':',
    );
  }

  public serverGroupIsSelected(serverGroup: IServerGroup): boolean {
    if (!this.serverGroups.length) {
      return false;
    }
    const key = this.makeServerGroupKey(serverGroup);
    return this.serverGroups.filter((sg) => sg.key === key).length > 0;
  }

  public toggleServerGroup(serverGroup: IServerGroup): void {
    const { $state } = ReactInjector;
    if (!ClusterState.filterModel.asFilterModel.sortFilter.multiselect) {
      const params = {
        provider: serverGroup.type,
        accountId: serverGroup.account,
        region: serverGroup.region,
        serverGroup: serverGroup.name,
        job: serverGroup.name,
      };
      if (this.isClusterChildState()) {
        if ($state.includes('**.serverGroup', params)) {
          return;
        }
        $state.go('^.' + serverGroup.category, params);
      } else {
        $state.go('.' + serverGroup.category, params);
      }
      return;
    }
    this.deselectAllInstances();
    const key = this.makeServerGroupKey(serverGroup);
    const selected = this.serverGroups.find((sg) => sg.key === key);
    if (selected) {
      this.serverGroups.splice(this.serverGroups.indexOf(selected), 1);
    } else {
      this.serverGroups.push({
        key,
        account: serverGroup.account,
        region: serverGroup.region,
        provider: serverGroup.type,
        name: serverGroup.name,
        moniker: serverGroup.moniker,
      });
    }
    this.serverGroupsStream.next();
    this.syncNavigation();
  }

  public toggleInstance(serverGroup: IServerGroup, instanceId: string): void {
    const { $state } = ReactInjector;
    if (!ClusterState.filterModel.asFilterModel.sortFilter.multiselect) {
      const params = { provider: serverGroup.type, instanceId };
      if (this.isClusterChildState()) {
        if ($state.includes('**.instanceDetails', params)) {
          return;
        }
        $state.go('^.instanceDetails', params);
      } else {
        $state.go('.instanceDetails', params);
      }
      return;
    }
    this.clearAllServerGroups();
    const group = this.getOrCreateInstanceGroup(serverGroup);
    if (group.instanceIds.includes(instanceId)) {
      group.instanceIds.splice(group.instanceIds.indexOf(instanceId), 1);
      group.selectAll = false;
    } else {
      group.instanceIds.push(instanceId);
    }
    this.instancesStream.next();
    this.syncNavigation();
  }

  public toggleSelectAll(serverGroup: IServerGroup, allInstanceIds?: string[]): void {
    const group = this.getOrCreateInstanceGroup(serverGroup);
    group.selectAll = !group.selectAll;
    group.instanceIds = group.selectAll ? allInstanceIds : [];
    if (group.selectAll) {
      this.clearAllServerGroups();
    }
    this.instancesStream.next();
    this.syncNavigation();
  }

  public instanceIsSelected(serverGroup: IServerGroup, instanceId: string): boolean {
    const group = this.getOrCreateInstanceGroup(serverGroup);
    return group.instanceIds.includes(instanceId);
  }

  private isClusterChildState(): boolean {
    return ReactInjector.$state.$current.name.split('.').pop() !== 'clusters';
  }
}
