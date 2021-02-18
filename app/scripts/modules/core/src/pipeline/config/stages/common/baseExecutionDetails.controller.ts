import { StateParams } from '@uirouter/angularjs';
import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import { IScope, module } from 'angular';

import {
  EXECUTION_DETAILS_SECTION_SERVICE,
  ExecutionDetailsSectionService,
} from '../../../details/executionDetailsSection.service';

export interface IExecutionDetailsScope extends IScope {
  configSections: string[];
  detailsSection: string;
}

export class BaseExecutionDetailsCtrl {
  public static $inject = ['$scope', '$stateParams', 'executionDetailsSectionService'];
  constructor(
    public $scope: IExecutionDetailsScope,
    protected $stateParams: StateParams,
    protected executionDetailsSectionService: ExecutionDetailsSectionService,
  ) {
    this.$scope.$on('$stateChangeSuccess', () => this.initialize());
    this.$scope.$watch('configSections', () => this.initialize());
  }

  public $onInit() {
    this.initialize();
  }

  protected initialize(): void {
    this.executionDetailsSectionService.synchronizeSection(this.$scope.configSections, () => this.initialized());
  }

  protected initialized(): void {
    this.$scope.detailsSection = this.$stateParams.details;
  }
}

export const BASE_EXECUTION_DETAILS_CTRL =
  'spinnaker.core.pipeline.config.stages.common.baseExecutionDetails.controller';

module(BASE_EXECUTION_DETAILS_CTRL, [UIROUTER_ANGULARJS, EXECUTION_DETAILS_SECTION_SERVICE]).controller(
  'BaseExecutionDetailsCtrl',
  BaseExecutionDetailsCtrl,
);
