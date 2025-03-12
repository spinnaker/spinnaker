import type { IController } from 'angular';
import { module } from 'angular';
import { difference } from 'lodash';

import type { CloudrunLoadBalancerUpsertDescription } from '../../loadBalancerTransformer';

class CloudrunLoadBalancerSettingsController implements IController {
  public loadBalancer: CloudrunLoadBalancerUpsertDescription;
  public serverGroupOptions: string[];
  public forPipelineConfig: boolean;

  public $onInit(): void {
    this.updateServerGroupOptions();
  }

  public addAllocation(): void {
    const remainingServerGroups = this.serverGroupsWithoutAllocation();
    if (remainingServerGroups.length) {
      this.loadBalancer.splitDescription.allocationDescriptions.push({
        revisionName: remainingServerGroups[0],
        percent: 0,
      });
      this.updateServerGroupOptions();
    } else if (this.forPipelineConfig) {
      this.loadBalancer.splitDescription.allocationDescriptions.push({
        percent: 0,
        revisionName: '',
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
        (sum, allocationDescription) => sum + allocationDescription.percent,
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

  public initializeAsTextInput(serverGroupName: string): boolean {
    if (this.forPipelineConfig) {
      return !this.loadBalancer.serverGroups.map((serverGroup) => serverGroup.name).includes(serverGroupName);
    } else {
      return false;
    }
  }

  private serverGroupsWithoutAllocation(): string[] {
    const serverGroupsWithAllocation = this.loadBalancer.splitDescription.allocationDescriptions.map(
      (description) => description.revisionName,
    );
    const allServerGroups = this.loadBalancer.serverGroups.map((serverGroup) => serverGroup.name);
    return difference(allServerGroups, serverGroupsWithAllocation);
  }
}

const cloudrunLoadBalancerSettingsComponent: ng.IComponentOptions = {
  bindings: { loadBalancer: '=', forPipelineConfig: '<', application: '<' },
  controller: CloudrunLoadBalancerSettingsController,
  templateUrl: require('./basicSettings.component.html'),
};

export const CLOUDRUN_LOAD_BALANCER_BASIC_SETTINGS = 'spinnaker.cloudrun.loadBalancerSettings.component';

module(CLOUDRUN_LOAD_BALANCER_BASIC_SETTINGS, []).component(
  'cloudrunLoadBalancerBasicSettings',
  cloudrunLoadBalancerSettingsComponent,
);
