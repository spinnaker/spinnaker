import { IController, module } from 'angular';
import { difference } from 'lodash';

import { AppengineLoadBalancerUpsertDescription } from '../../transformer';

class AppengineLoadBalancerSettingsController implements IController {
  public loadBalancer: AppengineLoadBalancerUpsertDescription;
  public serverGroupOptions: string[];
  public forPipelineConfig: boolean;

  public $onInit(): void {
    this.updateServerGroupOptions();
  }

  public addAllocation(): void {
    const remainingServerGroups = this.serverGroupsWithoutAllocation();
    if (remainingServerGroups.length) {
      this.loadBalancer.splitDescription.allocationDescriptions.push({
        serverGroupName: remainingServerGroups[0],
        allocation: 0,
        locatorType: 'fromExisting',
      });
      if (
        this.loadBalancer.splitDescription.allocationDescriptions.length > 1 &&
        !this.loadBalancer.splitDescription.shardBy
      ) {
        this.loadBalancer.splitDescription.shardBy = 'IP';
      }
      this.updateServerGroupOptions();
    } else if (this.forPipelineConfig) {
      this.loadBalancer.splitDescription.allocationDescriptions.push({
        allocation: 0,
        locatorType: 'text',
        serverGroupName: '',
      });
    }
  }

  public removeAllocation(index: number): void {
    this.loadBalancer.splitDescription.allocationDescriptions.splice(index, 1);
    this.updateServerGroupOptions();
  }

  public allocationIsInvalid(): boolean {
    return (
      this.loadBalancer.splitDescription.allocationDescriptions.reduce(
        (sum, allocationDescription) => sum + allocationDescription.allocation,
        0,
      ) !== 100
    );
  }

  public updateServerGroupOptions(): void {
    this.serverGroupOptions = this.serverGroupsWithoutAllocation();
  }

  public showAddButton(): boolean {
    if (this.forPipelineConfig) {
      return true;
    } else {
      return this.serverGroupsWithoutAllocation().length > 0;
    }
  }

  public showShardByOptions(): boolean {
    return this.loadBalancer.splitDescription.allocationDescriptions.length > 1 || this.loadBalancer.migrateTraffic;
  }

  public initializeAsTextInput(serverGroupName: string): boolean {
    if (this.forPipelineConfig) {
      return !this.loadBalancer.serverGroups.map((serverGroup) => serverGroup.name).includes(serverGroupName);
    } else {
      return false;
    }
  }

  private serverGroupsWithoutAllocation(): string[] {
    const serverGroupsWithAllocation = this.loadBalancer.splitDescription.allocationDescriptions.map(
      (description) => description.serverGroupName,
    );
    const allServerGroups = this.loadBalancer.serverGroups.map((serverGroup) => serverGroup.name);
    return difference(allServerGroups, serverGroupsWithAllocation);
  }
}

const appengineLoadBalancerSettingsComponent: ng.IComponentOptions = {
  bindings: { loadBalancer: '=', forPipelineConfig: '<', application: '<' },
  controller: AppengineLoadBalancerSettingsController,
  templateUrl: require('./basicSettings.component.html'),
};

export const APPENGINE_LOAD_BALANCER_BASIC_SETTINGS = 'spinnaker.appengine.loadBalancerSettings.component';

module(APPENGINE_LOAD_BALANCER_BASIC_SETTINGS, []).component(
  'appengineLoadBalancerBasicSettings',
  appengineLoadBalancerSettingsComponent,
);
