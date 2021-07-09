import { IController, module } from 'angular';
import { IModalInstanceService, IModalService } from 'angular-ui-bootstrap';
import { find, map } from 'lodash';

import { Application } from '@spinnaker/core';

import {
  GCE_LOAD_BALANCER_TYPE_TO_WIZARD_CONSTANT,
  IGceLoadBalancerToWizardMap,
} from './loadBalancerTypeToWizardMap.constant';

class GceLoadBalancerChoiceCtrl implements IController {
  public choices: string[];
  public choice = 'Network';

  public static $inject = ['$uibModal', '$uibModalInstance', 'application', 'loadBalancerTypeToWizardMap'];
  constructor(
    public $uibModal: IModalService,
    public $uibModalInstance: IModalInstanceService,
    private application: Application,
    private loadBalancerTypeToWizardMap: IGceLoadBalancerToWizardMap,
  ) {}

  public $onInit(): void {
    this.choices = map(this.loadBalancerTypeToWizardMap, (wizardConfig) => wizardConfig.label);
  }

  public choose(choice: string): void {
    const wizard = find(this.loadBalancerTypeToWizardMap, (wizardConfig) => wizardConfig.label === choice);
    this.$uibModalInstance.dismiss();
    this.$uibModal.open({
      templateUrl: wizard.createTemplateUrl,
      controller: `${wizard.controller} as ctrl`,
      size: 'lg',
      resolve: {
        application: () => this.application,
        loadBalancer: (): null => null,
        isNew: () => true,
        forPipelineConfig: () => false,
      },
    });
  }
}

export const GCE_LOAD_BALANCER_CHOICE_MODAL = 'spinnaker.gce.loadBalancerChoice.modal.controller';

module(GCE_LOAD_BALANCER_CHOICE_MODAL, [GCE_LOAD_BALANCER_TYPE_TO_WIZARD_CONSTANT]).controller(
  'gceLoadBalancerChoiceCtrl',
  GceLoadBalancerChoiceCtrl,
);
