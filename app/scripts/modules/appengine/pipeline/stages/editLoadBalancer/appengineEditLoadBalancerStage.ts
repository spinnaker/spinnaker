import {module} from 'angular';
import {IModalService} from 'angular-ui-bootstrap';
import {cloneDeep, get} from 'lodash';

import {CloudProviderRegistry} from 'core/cloudProvider/cloudProvider.registry';
import {LoadBalancerReader} from 'core/loadBalancer/loadBalancer.read.service';
import {ILoadBalancer} from 'core/domain/index';
import {APPENGINE_LOAD_BALANCER_CHOICE_MODAL_CTRL} from './loadBalancerChoice.modal.controller';

class AppengineEditLoadBalancerStageCtrl {
  static get $inject() { return ['$scope', '$uibModal', 'cloudProviderRegistry', 'loadBalancerReader']; }

  constructor(public $scope: any,
              private $uibModal: IModalService,
              private cloudProviderRegistry: CloudProviderRegistry,
              private loadBalancerReader: LoadBalancerReader) {
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

  public editLoadBalancer(loadBalancer: ILoadBalancer, index: number) {
    let config = this.cloudProviderRegistry.getValue('appengine', 'loadBalancer');
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
  APPENGINE_LOAD_BALANCER_CHOICE_MODAL_CTRL,
  require('core/config/settings.js'),
]).config((pipelineConfigProvider: any, settings: any) => {
    if (get(settings, 'providers.appengine.defaults.editLoadBalancerStageEnabled')) {
      pipelineConfigProvider.registerStage({
        label: 'Edit Load Balancer',
        description: 'Edits a load balancer',
        key: 'upsertLoadBalancers',
        cloudProvider: 'appengine',
        templateUrl: require('./editLoadBalancerStage.html'),
        controller: 'appengineEditLoadBalancerStageCtrl',
        controllerAs: 'editLoadBalancerStageCtrl',
        validators: [],
      });
    }
  })
  .controller('appengineEditLoadBalancerStageCtrl', AppengineEditLoadBalancerStageCtrl);
