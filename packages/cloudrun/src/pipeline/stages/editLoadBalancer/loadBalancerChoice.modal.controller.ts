import type { IController } from 'angular';
import { module } from 'angular';
import type { IModalService, IModalServiceInstance } from 'angular-ui-bootstrap';
import { cloneDeep } from 'lodash';

import type { Application, ILoadBalancer } from '@spinnaker/core';
import { CloudProviderRegistry } from '@spinnaker/core';

class CloudrunLoadBalancerChoiceModalCtrl implements IController {
  public state = { loading: true };
  public loadBalancers: ILoadBalancer[];
  public selectedLoadBalancer: ILoadBalancer;

  public static $inject = ['$uibModal', '$uibModalInstance', 'application'];
  constructor(
    private $uibModal: IModalService,
    private $uibModalInstance: IModalServiceInstance,
    private application: Application,
  ) {
    this.initialize();
  }

  public submit(): void {
    const config = CloudProviderRegistry.getValue('cloudrun', 'loadBalancer');
    const updatedLoadBalancerPromise = this.$uibModal.open({
      templateUrl: config.createLoadBalancerTemplateUrl,
      controller: `${config.createLoadBalancerController} as ctrl`,
      size: 'lg',
      resolve: {
        application: () => this.application,
        loadBalancer: () => cloneDeep(this.selectedLoadBalancer),
        isNew: () => false,
        forPipelineConfig: () => true,
      },
    }).result;

    this.$uibModalInstance.close(updatedLoadBalancerPromise);
  }

  public cancel(): void {
    this.$uibModalInstance.dismiss();
  }

  private initialize(): void {
    this.application
      .getDataSource('loadBalancers')
      .ready()
      .then(() => {
        this.loadBalancers = (this.application.loadBalancers.data as ILoadBalancer[]).filter(
          (candidate) => candidate.cloudProvider === 'cloudrun',
        );

        if (this.loadBalancers.length) {
          this.selectedLoadBalancer = this.loadBalancers[0];
        }
        this.state.loading = false;
      });
  }
}

export const CLOUDRUN_LOAD_BALANCER_CHOICE_MODAL_CTRL = 'spinnaker.Cloudrun.loadBalancerChoiceModal.controller';
module(CLOUDRUN_LOAD_BALANCER_CHOICE_MODAL_CTRL, []).controller(
  'cloudrunLoadBalancerChoiceModelCtrl',
  CloudrunLoadBalancerChoiceModalCtrl,
);
