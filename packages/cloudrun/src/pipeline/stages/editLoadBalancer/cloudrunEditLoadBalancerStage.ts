import type { IController } from 'angular';
import { module } from 'angular';
import type { IModalService } from 'angular-ui-bootstrap';
import { cloneDeep } from 'lodash';

import type { ILoadBalancer } from '@spinnaker/core';
import { CloudProviderRegistry, Registry } from '@spinnaker/core';

import { CLOUDRUN_LOAD_BALANCER_CHOICE_MODAL_CTRL } from './loadBalancerChoice.modal.controller';

class CloudrunEditLoadBalancerStageCtrl implements IController {
  public static $inject = ['$scope', '$uibModal'];
  constructor(public $scope: any, private $uibModal: IModalService) {
    $scope.stage.loadBalancers = $scope.stage.loadBalancers || [];
    $scope.stage.cloudProvider = 'cloudrun';
  }

  public addLoadBalancer(): void {
    this.$uibModal
      .open({
        templateUrl: require('./loadBalancerChoice.modal.html'),
        controller: `cloudrunLoadBalancerChoiceModelCtrl as ctrl`,
        resolve: {
          application: () => this.$scope.application,
        },
      })
      .result.then((newLoadBalancer: ILoadBalancer) => {
        this.$scope.stage.loadBalancers.push(newLoadBalancer);
      })
      .catch(() => {});
  }

  public editLoadBalancer(index: number) {
    const config = CloudProviderRegistry.getValue('cloudrun', 'loadBalancer');
    this.$uibModal
      .open({
        templateUrl: config.createLoadBalancerTemplateUrl,
        controller: `${config.createLoadBalancerController} as ctrl`,
        size: 'lg',
        resolve: {
          application: () => this.$scope.application,
          loadBalancer: () => cloneDeep(this.$scope.stage.loadBalancers[index]),
          isNew: () => false,
          forPipelineConfig: () => true,
        },
      })
      .result.then((updatedLoadBalancer: ILoadBalancer) => {
        this.$scope.stage.loadBalancers[index] = updatedLoadBalancer;
      })
      .catch(() => {});
  }

  public removeLoadBalancer(index: number): void {
    this.$scope.stage.loadBalancers.splice(index, 1);
  }
}

export const CLOUDRUN_EDIT_LOAD_BALANCER_STAGE = 'spinnaker.cloudrun.pipeline.stage.editLoadBalancerStage';
module(CLOUDRUN_EDIT_LOAD_BALANCER_STAGE, [CLOUDRUN_LOAD_BALANCER_CHOICE_MODAL_CTRL])
  .config(() => {
    Registry.pipeline.registerStage({
      label: 'Edit Load Balancer (Cloudrun)',
      description: 'Edits a load balancer',
      key: 'upsertCloudrunLoadBalancers',
      cloudProvider: 'cloudrun',
      templateUrl: require('./editLoadBalancerStage.html'),
      executionDetailsUrl: require('./editLoadBalancerExecutionDetails.html'),
      executionConfigSections: ['editLoadBalancerConfig', 'taskStatus'],
      controller: 'cloudrunEditLoadBalancerStageCtrl',
      controllerAs: 'editLoadBalancerStageCtrl',
      validators: [],
    });
  })
  .controller('cloudrunEditLoadBalancerStageCtrl', CloudrunEditLoadBalancerStageCtrl);
