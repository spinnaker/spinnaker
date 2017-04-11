import {module} from 'angular';
import {IModalService} from 'angular-ui-bootstrap';
import {cloneDeep} from 'lodash';

import {CloudProviderRegistry} from 'core/cloudProvider/cloudProvider.registry';
import {ILoadBalancer} from 'core/domain/index';
import {APPENGINE_EDIT_LOAD_BALANCER_EXECUTION_DETAILS_CTRL} from './appengineEditLoadBalancerExecutionDetails.controller';
import {APPENGINE_LOAD_BALANCER_CHOICE_MODAL_CTRL} from './loadBalancerChoice.modal.controller';
import {AppengineProviderSettings} from '../../../appengine.settings';

class AppengineEditLoadBalancerStageCtrl {
  static get $inject() { return ['$scope', '$uibModal', 'cloudProviderRegistry']; }

  constructor(public $scope: any,
              private $uibModal: IModalService,
              private cloudProviderRegistry: CloudProviderRegistry) {
    $scope.stage.loadBalancers = $scope.stage.loadBalancers || [];
    $scope.stage.cloudProvider = 'appengine';
  }

  public addLoadBalancer(): void {
    this.$uibModal.open({
      templateUrl: require('./loadBalancerChoice.modal.html'),
      controller: `appengineLoadBalancerChoiceModelCtrl as ctrl`,
      resolve: {
        application: () => this.$scope.application,
      }
    }).result.then((newLoadBalancer: ILoadBalancer) => {
      this.$scope.stage.loadBalancers.push(newLoadBalancer);
    });
  }

  public editLoadBalancer(index: number) {
    const config = this.cloudProviderRegistry.getValue('appengine', 'loadBalancer');
    this.$uibModal.open({
      templateUrl: config.createLoadBalancerTemplateUrl,
      controller: `${config.createLoadBalancerController} as ctrl`,
      size: 'lg',
      resolve: {
        application: () => this.$scope.application,
        loadBalancer: () => cloneDeep(this.$scope.stage.loadBalancers[index]),
        isNew: () => false,
        forPipelineConfig: () => true,
      }
    }).result.then((updatedLoadBalancer: ILoadBalancer) => {
      this.$scope.stage.loadBalancers[index] = updatedLoadBalancer;
    });
  }

  public removeLoadBalancer(index: number): void {
    this.$scope.stage.loadBalancers.splice(index, 1);
  }
}

export const APPENGINE_EDIT_LOAD_BALANCER_STAGE = 'spinnaker.appengine.pipeline.stage.editLoadBalancerStage';
module(APPENGINE_EDIT_LOAD_BALANCER_STAGE, [
  APPENGINE_EDIT_LOAD_BALANCER_EXECUTION_DETAILS_CTRL,
  APPENGINE_LOAD_BALANCER_CHOICE_MODAL_CTRL,
]).config((pipelineConfigProvider: any) => {
    if (AppengineProviderSettings.defaults.editLoadBalancerStageEnabled) {
      pipelineConfigProvider.registerStage({
        label: 'Edit Load Balancer',
        description: 'Edits a load balancer',
        key: 'upsertAppEngineLoadBalancers',
        cloudProvider: 'appengine',
        templateUrl: require('./editLoadBalancerStage.html'),
        executionDetailsUrl: require('./editLoadBalancerExecutionDetails.html'),
        controller: 'appengineEditLoadBalancerStageCtrl',
        controllerAs: 'editLoadBalancerStageCtrl',
        validators: [],
      });
    }
  })
  .controller('appengineEditLoadBalancerStageCtrl', AppengineEditLoadBalancerStageCtrl);
