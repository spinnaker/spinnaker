import { module } from 'angular';
import { find } from 'lodash';
import { StateParams } from '@uirouter/angularjs';

import { BaseExecutionDetailsCtrl, IExecutionDetailsScope } from '../core/baseExecutionDetails.controller';
import { EXECUTION_DETAILS_SECTION_SERVICE, ExecutionDetailsSectionService } from 'core/delivery/details/executionDetailsSection.service';
import { IStage } from 'core/domain';

export class ApplySourceServerGroupCapacityDetailsCtrl extends BaseExecutionDetailsCtrl {
  public parentDeployStage: IStage;

  constructor (public $scope: IExecutionDetailsScope,
               protected $stateParams: StateParams,
               protected executionDetailsSectionService: ExecutionDetailsSectionService) {
    'ngInject';
    super($scope, $stateParams, executionDetailsSectionService);
  }

  protected initialized(): void {
    super.initialized();
    this.parentDeployStage = find(this.$scope.execution.stages, (stage) => stage.id === this.$scope.stage.parentStageId);
  }
}

export const APPLY_SOURCE_SERVER_GROUP_CAPACITY_DETAILS_CTRL = 'spinnaker.core.pipeline.stage.applySourceServerGroupCapacityDetails.controller';

module(APPLY_SOURCE_SERVER_GROUP_CAPACITY_DETAILS_CTRL, [
  require('@uirouter/angularjs').default,
  EXECUTION_DETAILS_SECTION_SERVICE,
])
  .controller('applySourceServerGroupCapacityDetailsCtrl', ApplySourceServerGroupCapacityDetailsCtrl);
