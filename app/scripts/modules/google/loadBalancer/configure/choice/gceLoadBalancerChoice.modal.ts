import {module} from 'angular';
import {map, find} from 'lodash';

import {GCE_LOAD_BALANCER_TYPE_TO_WIZARD_CONSTANT,
        IGceLoadBalancerToWizardMap} from './loadBalancerTypeToWizardMap.constant';
import {Application} from 'core/application/application.model';

class GceLoadBalancerChoiceCtrl implements ng.IComponentController {
  public choices: string[];
  public choice: string = 'Network';

  static get $inject () { return ['$uibModal', '$uibModalInstance', 'application', 'loadBalancerTypeToWizardMap']; }

  constructor(public $uibModal: any,
              public $uibModalInstance: any,
              private application: Application,
              private loadBalancerTypeToWizardMap: IGceLoadBalancerToWizardMap) { }

  public $onInit (): void {
    this.choices = map(this.loadBalancerTypeToWizardMap, wizardConfig => wizardConfig.label);
  }

  public choose (choice: string): void {
    let wizard = find(this.loadBalancerTypeToWizardMap, wizardConfig => wizardConfig.label === choice);
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
      }
    });
  }
}

export const GCE_LOAD_BALANCER_CHOICE_MODAL = 'spinnaker.gce.loadBalancerChoice.modal.controller';

module(GCE_LOAD_BALANCER_CHOICE_MODAL, [
    GCE_LOAD_BALANCER_TYPE_TO_WIZARD_CONSTANT
  ])
  .controller('gceLoadBalancerChoiceCtrl', GceLoadBalancerChoiceCtrl);
