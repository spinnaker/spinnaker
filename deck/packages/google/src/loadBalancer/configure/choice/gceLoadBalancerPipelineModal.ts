import type { IModalService } from 'angular-ui-bootstrap';

import type { Application } from '@spinnaker/core';

import type { IGceLoadBalancerWizardConfig } from './loadBalancerTypeToWizardMap.constant';

export interface IGCEPipelineLoadBalancerModalOptions {
  application: Application;
  loadBalancer: any;
  isNew: boolean;
  $uibModal: IModalService;
}

export const openGCEPipelineLoadBalancerModal = ({
  application,
  loadBalancer,
  isNew,
  $uibModal,
}: IGCEPipelineLoadBalancerModalOptions): PromiseLike<any> => {
  return $uibModal
    .open({
      templateUrl: require('./gceLoadBalancerChoice.modal.html'),
      controller: 'gceLoadBalancerChoiceCtrl as ctrl',
      size: 'lg',
      resolve: {
        application: () => application,
        forPipelineConfig: () => true,
      },
    })
    .result.then((wizardConfig: IGceLoadBalancerWizardConfig) => {
      if (!wizardConfig) {
        return null;
      }
      const templateUrl = isNew ? wizardConfig.createTemplateUrl : wizardConfig.editTemplateUrl;
      return $uibModal.open({
        templateUrl,
        controller: `${wizardConfig.controller} as ctrl`,
        size: 'lg',
        resolve: {
          application: () => application,
          loadBalancer: () => loadBalancer,
          isNew: () => isNew,
          forPipelineConfig: () => true,
        },
      }).result;
    });
};
