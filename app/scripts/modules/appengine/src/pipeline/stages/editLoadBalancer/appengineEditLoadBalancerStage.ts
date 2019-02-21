import { IController, module } from 'angular';
import { IModalService } from 'angular-ui-bootstrap';
import { cloneDeep } from 'lodash';

import { CloudProviderRegistry, ILoadBalancer, Registry } from '@spinnaker/core';

import { APPENGINE_LOAD_BALANCER_CHOICE_MODAL_CTRL } from './loadBalancerChoice.modal.controller';

class AppengineEditLoadBalancerStageCtrl implements IController {
  public static $inject = ['$scope', '$uibModal'];
  constructor(public $scope: any, private $uibModal: IModalService) {
    $scope.stage.loadBalancers = $scope.stage.loadBalancers || [];
    $scope.stage.cloudProvider = 'appengine';
  }

  public addLoadBalancer(): void {
    this.$uibModal
      .open({
        templateUrl: require('./loadBalancerChoice.modal.html'),
        controller: `appengineLoadBalancerChoiceModelCtrl as ctrl`,
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
    const config = CloudProviderRegistry.getValue('appengine', 'loadBalancer');
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

export const APPENGINE_EDIT_LOAD_BALANCER_STAGE = 'spinnaker.appengine.pipeline.stage.editLoadBalancerStage';
module(APPENGINE_EDIT_LOAD_BALANCER_STAGE, [APPENGINE_LOAD_BALANCER_CHOICE_MODAL_CTRL])
  .config(() => {
    Registry.pipeline.registerStage({
      label: 'Edit Load Balancer',
      description: 'Edits a load balancer',
      key: 'upsertAppEngineLoadBalancers',
      cloudProvider: 'appengine',
      templateUrl: require('./editLoadBalancerStage.html'),
      executionDetailsUrl: require('./editLoadBalancerExecutionDetails.html'),
      executionConfigSections: ['editLoadBalancerConfig', 'taskStatus'],
      controller: 'appengineEditLoadBalancerStageCtrl',
      controllerAs: 'editLoadBalancerStageCtrl',
      validators: [],
    });
  })
  .controller('appengineEditLoadBalancerStageCtrl', AppengineEditLoadBalancerStageCtrl);
