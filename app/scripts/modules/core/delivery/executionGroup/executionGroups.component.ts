import {IComponentController, IComponentOptions, module} from 'angular';
import {IStateService} from 'angular-ui-router';

import {EXECUTION_FILTER_MODEL, ExecutionFilterModel} from 'core/delivery/filter/executionFilter.model';
import {EXECUTION_GROUP_COMPONENT} from './executionGroup.component';
import {Application} from 'core/application/application.model';

import './executionGroups.less';

export class ExecutionGroupsController implements IComponentController {
  public application: Application;

  public groups: any;
  public sortFilter: any;

  static get $inject(): string[] { return ['$state', 'executionFilterModel']; }

  constructor(private $state: IStateService,
              private executionFilterModel: ExecutionFilterModel) {}

  public $onInit(): void {
    this.groups = this.executionFilterModel.groups;
    this.sortFilter = this.executionFilterModel.sortFilter;
  }

  public showingDetails(): boolean {
    return this.$state.includes('**.execution');
  }
}

export class ExecutionGroupsComponent implements IComponentOptions {
  public bindings: any = {
    application: '<',
  };
  public controller: any = ExecutionGroupsController;
  public templateUrl: string = require('./executionGroups.component.html');
}

export const EXECUTION_GROUPS_COMPONENT = 'spinnaker.core.delivery.main.executionGroups.component';
module(EXECUTION_GROUPS_COMPONENT, [
  EXECUTION_FILTER_MODEL,
  EXECUTION_GROUP_COMPONENT,
])
.component('executionGroups', new ExecutionGroupsComponent());
