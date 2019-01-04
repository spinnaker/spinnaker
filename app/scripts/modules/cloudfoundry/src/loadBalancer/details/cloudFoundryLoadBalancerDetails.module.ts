import { IController, IQService, IScope, module } from 'angular';
import { react2angular } from 'react2angular';

import { CloudFoundryLoadBalancerDetails } from './CloudFoundryLoadBalancerDetails';
import { Application, ConfirmationModalService, ILoadBalancer } from '@spinnaker/core';

import { ICloudFoundryLoadBalancer } from 'cloudfoundry/domain';

interface ILoadBalancerFromStateParams {
  accountId: string;
  name: string;
  region: string;
}

class CloudFoundryLoadBalancerDetailsCtrl implements IController {
  private loadBalancerFromParams: ILoadBalancerFromStateParams;
  public loadBalancer: ICloudFoundryLoadBalancer;

  constructor(
    public $scope: IScope,
    private app: Application,
    private confirmationModalService: ConfirmationModalService,
    loadBalancer: ILoadBalancerFromStateParams,
    private $q: IQService,
  ) {
    'ngInject';
    this.$scope.application = this.app;
    this.$scope.confirmationModalService = this.confirmationModalService;
    this.$scope.loading = true;
    this.$scope.qService = this.$q;
    this.loadBalancerFromParams = loadBalancer;
    this.app
      .getDataSource('loadBalancers')
      .ready()
      .then(() => this.extractLoadBalancer());
  }

  private extractLoadBalancer(): void {
    this.$scope.loadBalancer = this.app.getDataSource('loadBalancers').data.find((test: ILoadBalancer) => {
      return test.name === this.loadBalancerFromParams.name && test.account === this.loadBalancerFromParams.accountId;
    }) as ICloudFoundryLoadBalancer;
    if (this.$scope.loadBalancer) {
      this.$scope.loading = false;
      this.app.getDataSource('loadBalancers').onRefresh(this.$scope, () => this.extractLoadBalancer());
    } else {
      this.$scope.loadBalancerNotFound = this.loadBalancerFromParams.name;
      this.$scope.loading = false;
      this.autoClose();
    }
  }

  private autoClose(): void {
    if (this.$scope.$$destroyed) {
      return;
    } else {
      this.$scope.params.allowModalToStayOpen = true;
    }
  }
}

export const CLOUD_FOUNDRY_LOAD_BALANCER_DETAILS = 'spinnaker.cloudfoundry.loadBalancerDetails';
module(CLOUD_FOUNDRY_LOAD_BALANCER_DETAILS, [])
  .component(
    'cfLoadBalancerDetails',
    react2angular(CloudFoundryLoadBalancerDetails, [
      'application',
      'confirmationModalService',
      'loadBalancer',
      'loadBalancerNotFound',
      'loading',
    ]),
  )
  .controller('cloudfoundryLoadBalancerDetailsCtrl', CloudFoundryLoadBalancerDetailsCtrl);
