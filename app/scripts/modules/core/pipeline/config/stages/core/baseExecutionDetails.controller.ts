import {IScope} from 'angular';
import {IStateParamsService} from 'angular-ui-router';

import {ExecutionDetailsSectionService} from 'core/delivery/details/executionDetailsSection.service';

export interface IExecutionDetailsScope extends IScope {
  configSections: string[];
  detailsSection: string;
}

export interface IExecutionDetailsStateParams extends IStateParamsService {
  details: string;
}

export class BaseExecutionDetailsCtrl {
  constructor (public $scope: IExecutionDetailsScope,
               private $stateParams: IExecutionDetailsStateParams,
               private executionDetailsSectionService: ExecutionDetailsSectionService) {
    this.$scope.$on('$stateChangeSuccess', () => this.initialize());
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
