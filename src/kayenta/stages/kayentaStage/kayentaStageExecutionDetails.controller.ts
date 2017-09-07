import { IScope, module } from 'angular';
import { StateParams } from '@uirouter/angularjs';

import {
  ExecutionDetailsSectionService,
  IExecutionStage
} from '@spinnaker/core';
import { RUN_CANARY } from './stageTypes';

class KayentaStageExecutionDetailsController {

  public canaryRuns: IExecutionStage[];

  constructor(public $scope: IScope,
              private $stateParams: StateParams,
              private executionDetailsSectionService: ExecutionDetailsSectionService) {
    'ngInject';
    this.$scope.configSections = ['canarySummary', 'canaryConfig', 'taskStatus'];
    this.$scope.$on('$stateChangeSuccess', () => this.initialize());
  }

  public $onInit(): void {
    this.initialize();
  }

  private initialize(): void {
    this.executionDetailsSectionService.synchronizeSection(this.$scope.configSections, () => this.initialized());
    this.setCanaryRuns();
  }

  private setCanaryRuns(): void {
    // The kayentaStageTransformer pushes related 'runCanary' and 'wait' stages
    // into the 'kayentaCanary' tasks list.
    this.canaryRuns = this.$scope.stage.tasks.filter((t: IExecutionStage) => t.type === RUN_CANARY);
  }

  private initialized(): void {
    this.$scope.detailsSection = this.$stateParams.details;
  }
}

export const KAYENTA_STAGE_EXECUTION_DETAILS_CONTROLLER = 'spinnaker.kayenta.kayentaStageExecutionDetails.controller';
module(KAYENTA_STAGE_EXECUTION_DETAILS_CONTROLLER, [])
  .controller('kayentaStageExecutionDetailsCtrl', KayentaStageExecutionDetailsController)
  .filter('dateToMillis', () => Date.parse);
