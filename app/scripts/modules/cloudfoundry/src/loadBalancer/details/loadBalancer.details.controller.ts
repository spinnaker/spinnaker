import { IController, IScope, module } from 'angular';

import { StateService } from '@uirouter/angularjs';

import {
  Application,
  CONFIRMATION_MODAL_SERVICE,
  ConfirmationModalService,
  ILoadBalancer,
  LoadBalancerWriter,
  ILoadBalancerDeleteCommand,
} from '@spinnaker/core';

import { ICloudFoundryLoadBalancer } from 'cloudfoundry/domain';

interface ILoadBalancerFromStateParams {
  accountId: string;
  region: string;
  name: string;
}

class CloudFoundryLoadBalancerDetailsController implements IController {
  public state = { loading: true };
  private loadBalancerFromParams: ILoadBalancerFromStateParams;
  public loadBalancer: ICloudFoundryLoadBalancer;
  public dispatchRules: string[] = [];

  constructor(
    private $state: StateService,
    private $scope: IScope,
    loadBalancer: ILoadBalancerFromStateParams,
    private app: Application,
    private confirmationModalService: ConfirmationModalService,
  ) {
    'ngInject';
    this.loadBalancerFromParams = loadBalancer;
    this.app
      .getDataSource('loadBalancers')
      .ready()
      .then(() => this.extractLoadBalancer());
  }

  public deleteLoadBalancer(): void {
    const taskMonitor = {
      application: this.app,
      title: 'Deleting ' + this.loadBalancer.name,
    };

    const submitMethod = () => {
      const loadBalancer: ILoadBalancerDeleteCommand = {
        cloudProvider: this.loadBalancer.cloudProvider,
        credentials: this.loadBalancer.account,
        regions: [this.loadBalancer.region],
        loadBalancerName: this.loadBalancer.name,
      };
      return LoadBalancerWriter.deleteLoadBalancer(loadBalancer, this.app);
    };

    this.confirmationModalService.confirm({
      header: 'Really delete ' + this.loadBalancer.name + '?',
      buttonText: 'Delete ' + this.loadBalancer.name,
      account: this.loadBalancer.account,
      taskMonitorConfig: taskMonitor,
      submitMethod,
    });
  }

  private extractLoadBalancer(): void {
    this.loadBalancer = this.app.getDataSource('loadBalancers').data.find((test: ILoadBalancer) => {
      return test.name === this.loadBalancerFromParams.name && test.account === this.loadBalancerFromParams.accountId;
    }) as ICloudFoundryLoadBalancer;
    if (this.loadBalancer) {
      this.state.loading = false;
      this.app.getDataSource('loadBalancers').onRefresh(this.$scope, () => this.extractLoadBalancer());
    } else {
      this.autoClose();
    }
  }

  private autoClose(): void {
    if (this.$scope.$$destroyed) {
      return;
    } else {
      this.$state.params.allowModalToStayOpen = true;
      this.$state.go('^', null, { location: 'replace' });
    }
  }
}

export const CLOUD_FOUNDRY_LOAD_BALANCER_DETAILS_CTRL = 'spinnaker.cloudfoundry.loadBalancerDetails.controller';
module(CLOUD_FOUNDRY_LOAD_BALANCER_DETAILS_CTRL, [CONFIRMATION_MODAL_SERVICE]).controller(
  'cfLoadBalancerDetailsCtrl',
  CloudFoundryLoadBalancerDetailsController,
);
