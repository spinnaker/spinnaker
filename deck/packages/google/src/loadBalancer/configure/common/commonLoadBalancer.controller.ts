import type { StateService } from '@uirouter/angularjs';
import type { IScope } from 'angular';
import type { IModalInstanceService } from 'angular-ui-bootstrap';
import { trimEnd } from 'lodash';

import type { Application } from '@spinnaker/core';
import { InfrastructureCaches } from '@spinnaker/core';
import type { IGceLoadBalancer } from '../../../domain/loadBalancer';

export class CommonGceLoadBalancerCtrl {
  public static $inject = ['$scope', 'application', '$uibModalInstance', '$state'];
  constructor(
    public $scope: IScope,
    public application: Application,
    public $uibModalInstance: IModalInstanceService,
    private $state: StateService,
  ) {}

  public onTaskComplete(loadBalancer: IGceLoadBalancer): void {
    InfrastructureCaches.clearCache('healthChecks');
    this.application.getDataSource('loadBalancers').refresh();
    this.application
      .getDataSource('loadBalancers')
      .onNextRefresh(this.$scope, () => this.onApplicationRefresh(loadBalancer));
  }

  public cancel(): void {
    this.$uibModalInstance.dismiss();
  }

  public getName(lb: IGceLoadBalancer, application: Application): string {
    const loadBalancerName = [application.name, lb.stack || '', lb.detail || ''].join('-');
    return trimEnd(loadBalancerName, '-');
  }

  private onApplicationRefresh(loadBalancer: IGceLoadBalancer): void {
    // If the user has already closed the modal, do not navigate to the new details view
    if (this.$scope.$$destroyed) {
      return;
    }
    this.$uibModalInstance.close();

    const newStateParams = {
      name: loadBalancer.loadBalancerName,
      accountId: loadBalancer.credentials,
      region: loadBalancer.region,
      provider: 'gce',
    };

    if (!this.$state.includes('**.loadBalancerDetails')) {
      this.$state.go('.loadBalancerDetails', newStateParams);
    } else {
      this.$state.go('^.loadBalancerDetails', newStateParams);
    }
  }
}
