import {module} from 'angular';
import {reduce, map} from 'lodash';

import {ServerGroup} from 'core/domain/index';
import {IAppengineLoadBalancer} from 'appengine/domain/index';

class AppengineLoadBalancerSettingsController implements ng.IComponentController {
  public loadBalancer: IAppengineLoadBalancer;
  public allocationsList: {serverGroupName: string, percent: number}[];
  public serverGroupOptions: string[];
  public forPipelineConfig: boolean;

  public $onInit(): void {
    this.allocationsList = map(this.loadBalancer.split.allocations, (percent: number, serverGroupName: string) => {
      return { serverGroupName, percent };
    });
    this.updateServerGroupOptions();
  }

  public addAllocation(): void {
    let remainingServerGroups = this.serverGroupsWithoutAllocation();
    if (remainingServerGroups.length) {
      this.allocationsList.push({ serverGroupName: remainingServerGroups[0].name, percent: 0});
      if (this.allocationsList.length > 1 && !this.loadBalancer.split.shardBy) {
        this.loadBalancer.split.shardBy = 'IP';
      }
      this.onAllocationChange();
    } else if (this.forPipelineConfig) {
      this.allocationsList.push({serverGroupName: '', percent: 0});
    }
  }

  public removeAllocation(index: number): void {
    this.allocationsList.splice(index, 1);
    this.onAllocationChange();
  }

  public onAllocationChange(): void {
    this.loadBalancer.split.allocations = this.allocationsList.reduce((allocations, allocation) => {
      if (allocation.serverGroupName) {
        allocations[allocation.serverGroupName] = allocation.percent;
      }
      return allocations;
    }, {} as {[serverGroupName: string]: number});
    this.updateServerGroupOptions();
  }

  public allocationIsInvalid(): boolean {
    return reduce(this.loadBalancer.split.allocations, (sum, percent) => sum + percent, 0) !== 100;
  }

  public updateServerGroupOptions(): void {
    this.serverGroupOptions = this.serverGroupsWithoutAllocation().map(serverGroup => serverGroup.name);
  }

  public showAddButton(): boolean {
    if (this.forPipelineConfig) {
      return true;
    } else {
      return this.serverGroupsWithoutAllocation().length > 0;
    }
  }

  public showShardByOptions(): boolean {
    return this.allocationsList.length > 1 || this.loadBalancer.migrateTraffic;
  }

  public initializeAsTextInput(serverGroupName: string): boolean {
    if (this.forPipelineConfig) {
      return !this.loadBalancer.serverGroups.map(serverGroup => serverGroup.name).includes(serverGroupName);
    } else {
      return false;
    }
  }

  private serverGroupsWithoutAllocation(): ServerGroup[] {
    return this.loadBalancer.serverGroups
      .filter((serverGroup: ServerGroup) => !(serverGroup.name in this.loadBalancer.split.allocations));
  }
}

class AppengineLoadBalancerSettingsComponent implements ng.IComponentOptions {
  public bindings: any = {loadBalancer: '=', forPipelineConfig: '<'};
  public controller: any = AppengineLoadBalancerSettingsController;
  public templateUrl: string = require('./basicSettings.component.html');
}

export const APPENGINE_LOAD_BALANCER_BASIC_SETTINGS = 'spinnaker.appengine.loadBalancerSettings.component';

module(APPENGINE_LOAD_BALANCER_BASIC_SETTINGS, [])
  .component('appengineLoadBalancerBasicSettings', new AppengineLoadBalancerSettingsComponent());
