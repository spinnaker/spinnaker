import { IScope, module } from 'angular';
import { StateParams } from '@uirouter/angularjs';

import {
  ExecutionDetailsSectionService,
  IExecutionStage
} from '@spinnaker/core';
import { RUN_CANARY } from './stageTypes';
import { CANARY_RUN_SUMMARIES_COMPONENT } from './canaryRunSummaries.component';
import { ICanaryConfigSummary } from 'kayenta/domain/ICanaryConfigSummary';

import './kayentaStageExecutionDetails.less';

class KayentaStageExecutionDetailsController {

  public canaryRuns: IExecutionStage[];
  public canaryConfigName: string;
  public firstScopeName: string;
  public resolvedControl: string;
  public resolvedExperiment: string;

  constructor(public $scope: IScope,
              private $stateParams: StateParams,
              private executionDetailsSectionService: ExecutionDetailsSectionService) {
    'ngInject';
    this.$scope.configSections = ['canarySummary', 'canaryConfig', 'taskStatus'];
    this.$scope.$on('$stateChangeSuccess', () => this.initialize());
    this.$scope.application.ready().then(() => {
      const canaryConfigSummary =
        this.$scope.application.getDataSource('canaryConfigs').data.find(
          (config: ICanaryConfigSummary) => config.id === this.$scope.stage.context.canaryConfig.canaryConfigId
        );
      if (canaryConfigSummary) {
        this.canaryConfigName = canaryConfigSummary.name;
      }
    });
  }

  public $onInit(): void {
    this.initialize();
  }

  private initialize(): void {
    this.executionDetailsSectionService.synchronizeSection(this.$scope.configSections, () => this.initialized());
    this.setCanaryRuns();
    this.resolveFirstScopeName();
    this.resolveControlAndExperimentNames();
    this.$scope.$watchCollection('stage.tasks', () => this.setCanaryRuns());
  }

  private setCanaryRuns(): void {
    // The kayentaStageTransformer pushes related 'runCanary' and 'wait' stages
    // into the 'kayentaCanary' tasks list.
    this.canaryRuns = this.$scope.stage.tasks.filter((t: IExecutionStage) => t.type === RUN_CANARY);
  }

  private resolveFirstScopeName(): void {
    this.firstScopeName = this.$scope.stage.context.canaryConfig.scopes[0].scopeName;
  }

  private resolveControlAndExperimentNames(): void {
    this.resolvedControl = this.canaryRuns.length ? this.canaryRuns[0].context.scopes[this.firstScopeName].controlScope.scope : this.$scope.stage.context.canaryConfig.scopes[0].controlScope;
    this.resolvedExperiment = this.canaryRuns.length ? this.canaryRuns[0].context.scopes[this.firstScopeName].experimentScope.scope : this.$scope.stage.context.canaryConfig.scopes[0].experimentScope;
  }

  private initialized(): void {
    this.$scope.detailsSection = this.$stateParams.details;
  }
}

export const KAYENTA_STAGE_EXECUTION_DETAILS_CONTROLLER = 'spinnaker.kayenta.kayentaStageExecutionDetails.controller';
module(KAYENTA_STAGE_EXECUTION_DETAILS_CONTROLLER, [
  CANARY_RUN_SUMMARIES_COMPONENT,
]).controller('kayentaStageExecutionDetailsCtrl', KayentaStageExecutionDetailsController)
  .filter('dateToMillis', () => Date.parse);
