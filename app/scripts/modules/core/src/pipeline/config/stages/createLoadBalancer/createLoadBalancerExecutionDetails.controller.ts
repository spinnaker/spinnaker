import { StateParams } from '@uirouter/angularjs';
import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import { module } from 'angular';
import { forEach } from 'lodash';

import { BaseExecutionDetailsCtrl, IExecutionDetailsScope } from '../common/baseExecutionDetails.controller';
import {
  EXECUTION_DETAILS_SECTION_SERVICE,
  ExecutionDetailsSectionService,
} from '../../../details/executionDetailsSection.service';

export class CreateLoadBalancerDetailsCtrl extends BaseExecutionDetailsCtrl {
  public static $inject = ['$scope', '$stateParams', 'executionDetailsSectionService'];
  constructor(
    public $scope: IExecutionDetailsScope,
    protected $stateParams: StateParams,
    protected executionDetailsSectionService: ExecutionDetailsSectionService,
  ) {
    super($scope, $stateParams, executionDetailsSectionService);

    $scope.configSections = ['loadBalancerConfig', 'taskStatus'];
  }

  protected initialized(): void {
    super.initialized();

    const context = this.$scope.stage.context || {};
    const results: any[] = [];

    if (context && context['kato.tasks'] && context['kato.tasks'].length) {
      const resultObjects = context['kato.tasks'][0].resultObjects;
      if (resultObjects && resultObjects.length) {
        const addCreatedArtifacts = (key: string) => {
          const createdArtifacts = resultObjects;
          if (createdArtifacts) {
            createdArtifacts.forEach((artifact: any) => {
              forEach(artifact[key], (valueObj, region) => {
                const result = {
                  type: 'loadBalancers',
                  application: context.application,
                  name: valueObj.name,
                  region,
                  account: context.account,
                  dnsName: valueObj.dnsName,
                  provider: context.providerType || context.cloudProvider || 'aws',
                };
                results.push(result);
              });
            });
          }
        };
        addCreatedArtifacts('loadBalancers');
      }
    }
    this.$scope.createdLoadBalancers = results;
    this.$scope.provider = context.cloudProvider || context.providerType || 'aws';
  }
}

export const CREATE_LOAD_BALANCER_EXECUTION_DETAILS_CTRL =
  'spinnaker.core.pipeline.stage.createLoadBalancer.executionDetails.controller';
module(CREATE_LOAD_BALANCER_EXECUTION_DETAILS_CTRL, [UIROUTER_ANGULARJS, EXECUTION_DETAILS_SECTION_SERVICE]).controller(
  'createLoadBalancerExecutionDetailsCtrl',
  CreateLoadBalancerDetailsCtrl,
);
