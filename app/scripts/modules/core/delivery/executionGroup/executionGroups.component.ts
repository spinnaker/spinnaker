import {IComponentController, IComponentOptions, module} from 'angular';
import {IStateService} from 'angular-ui-router';

import {EXECUTION_GROUP_COMPONENT} from './executionGroup.component';
import {Application} from 'core/application/application.model';

import './executionGroups.less';

export class ExecutionGroupsController implements IComponentController {
  public application: Application;

  public groups: any;
  public sortFilter: any;

  static get $inject(): string[] { return ['$state', 'ExecutionFilterModel']; }

  constructor(private $state: IStateService,
              private ExecutionFilterModel: any) {}

  public $onInit(): void {
    this.groups = this.ExecutionFilterModel.groups;
    this.sortFilter = this.ExecutionFilterModel.sortFilter;
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
  require('../filter/executionFilter.model.js'),
  EXECUTION_GROUP_COMPONENT,
])
.component('executionGroups', new ExecutionGroupsComponent());
