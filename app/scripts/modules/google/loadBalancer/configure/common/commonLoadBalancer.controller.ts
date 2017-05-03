import {IScope} from 'angular';
import {StateService} from 'angular-ui-router';
import {trimEnd} from 'lodash';

import {Application} from 'core/application/application.model';
import {IGceLoadBalancer} from 'google/domain/loadBalancer';
import {InfrastructureCacheService} from 'core/cache/infrastructureCaches.service';

interface IPrivateScope extends IScope {
  $$destroyed: boolean;
}

export class CommonGceLoadBalancerCtrl {
  constructor (public $scope: IPrivateScope,
               public application: Application,
               public $uibModalInstance: any,
               private $state: StateService,
               private infrastructureCaches: InfrastructureCacheService) { }

  public onTaskComplete (loadBalancer: IGceLoadBalancer): void {
    this.infrastructureCaches.clearCache('healthCheck');
    this.application.getDataSource('loadBalancers').refresh();
    this.application.getDataSource('loadBalancers')
      .onNextRefresh(this.$scope, () => this.onApplicationRefresh(loadBalancer));
  }

  public cancel (): void {
    this.$uibModalInstance.dismiss();
  }

  public getName (lb: IGceLoadBalancer, application: Application): string {
    const loadBalancerName = [application.name, (lb.stack || ''), (lb.detail || '')].join('-');
    return trimEnd(loadBalancerName, '-');
  }

  private onApplicationRefresh (loadBalancer: IGceLoadBalancer): void {
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
