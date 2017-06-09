import { IComponentController, IComponentOptions, module } from 'angular';

import { INFRASTRUCTURE_CACHE_SERVICE, InfrastructureCacheService } from '@spinnaker/core';

import { AWS_SERVER_GROUP_CONFIGURATION_SERVICE, AwsServerGroupConfigurationService } from 'amazon/serverGroup/configure/serverGroupConfiguration.service';

class TargetGroupSelectorController implements IComponentController {
  public command: any;

  public refreshTime: number;
  public refreshing = false;

  constructor(private awsServerGroupConfigurationService: AwsServerGroupConfigurationService,
              private infrastructureCaches: InfrastructureCacheService) {
    'ngInject';

    this.setLoadBalancerRefreshTime();
  }

  public setLoadBalancerRefreshTime(): void {
    this.refreshTime = this.infrastructureCaches.get('loadBalancers').getStats().ageMax;
  }

  public refreshLoadBalancers(): void {
    this.refreshing = true;
    this.awsServerGroupConfigurationService.refreshLoadBalancers(this.command).then(() => {
      this.refreshing = false;
      this.setLoadBalancerRefreshTime();
    });
  }
}

export class TargetGroupSelectorComponent implements IComponentOptions {
  public bindings: any = {
    command: '='
  };
  public controller: any = TargetGroupSelectorController;
  public templateUrl = require('./targetGroupSelector.component.html');
}

export const TARGET_GROUP_SELECTOR = 'spinnaker.amazon.serverGroup.configure.wizard.targetGroups.selector.component';
module (TARGET_GROUP_SELECTOR, [
  AWS_SERVER_GROUP_CONFIGURATION_SERVICE,
  INFRASTRUCTURE_CACHE_SERVICE
])
  .component('awsServerGroupTargetGroupSelector', new TargetGroupSelectorComponent());
