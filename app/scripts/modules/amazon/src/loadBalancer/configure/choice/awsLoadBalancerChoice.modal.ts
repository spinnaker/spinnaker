import { IComponentController, module } from 'angular';
import { IModalInstanceService } from 'angular-ui-bootstrap';

import { Application } from '@spinnaker/core';

import { IAwsLoadBalancerConfig, LoadBalancerTypes } from './LoadBalancerTypes';

import './awsLoadBalancerChoice.less'

class AwsLoadBalancerChoiceCtrl implements IComponentController {
  public choices: IAwsLoadBalancerConfig[];
  public choice: IAwsLoadBalancerConfig;

  constructor(private $uibModal: any,
              private $uibModalInstance: IModalInstanceService,
              private application: Application) {
    'ngInject';
  }

  public $onInit(): void {
    this.choices = LoadBalancerTypes;
    this.choice = this.choices[0];
  }

  public onChoiceSelection(choice: IAwsLoadBalancerConfig): void {
    this.choice = choice;
  }

  public choose(): void {
    this.$uibModalInstance.dismiss();
    this.$uibModal.open({
      templateUrl: this.choice.createTemplateUrl,
      controller: `${this.choice.controller} as ctrl`,
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

export const AWS_LOAD_BALANCER_CHOICE_MODAL = 'spinnaker.amazon.loadBalancerChoice.modal.controller';

module(AWS_LOAD_BALANCER_CHOICE_MODAL, [])
  .controller('awsLoadBalancerChoiceCtrl', AwsLoadBalancerChoiceCtrl);
