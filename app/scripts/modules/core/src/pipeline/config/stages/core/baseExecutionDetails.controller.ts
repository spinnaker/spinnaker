import { IScope, module } from 'angular';
import { StateParams } from '@uirouter/angularjs';

import { ExecutionDetailsSectionService } from 'core/delivery/details/executionDetailsSection.service';

export interface IExecutionDetailsScope extends IScope {
  configSections: string[];
  detailsSection: string;
}

export class BaseExecutionDetailsCtrl {
  constructor (public $scope: IExecutionDetailsScope,
               protected $stateParams: StateParams,
               protected executionDetailsSectionService: ExecutionDetailsSectionService) {
    this.$scope.$on('$stateChangeSuccess', () => this.initialize());
  }

  public $onInit() {
    this.initialize();
  }

  protected setScopeConfigSections(sections: string[]): void {
    this.$scope.configSections = sections;
  }

  protected initialize(): void {
    this.executionDetailsSectionService.synchronizeSection(this.$scope.configSections, () => this.initialized());
  }

  private initialized(): void {
    this.$scope.detailsSection = this.$stateParams.details;
  }
}

export const BASE_EXECUTION_DETAILS_CTRL = 'spinnaker.core.pipeline.config.stages.core.baseExecutionDetails.controller';

module(BASE_EXECUTION_DETAILS_CTRL, []).controller('BaseExecutionDetailsCtrl', BaseExecutionDetailsCtrl);
