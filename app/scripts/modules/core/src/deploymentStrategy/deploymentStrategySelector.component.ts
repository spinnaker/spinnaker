import { IController, IComponentOptions, module } from 'angular';
import { unset } from 'lodash';

import { DeploymentStrategyRegistry, IDeploymentStrategy } from 'core/deploymentStrategy';

export interface IDeploymentCommand {
  selectedProvider?: string;
  cloudProvider?: string;
  strategy: string;
}

export class DeploymentStrategySelectorController implements IController {
  public command: IDeploymentCommand;
  public labelColumns = '3';
  public fieldColumns = '6';
  public strategies: IDeploymentStrategy[];
  public currentStrategy: string;
  public additionalFieldsTemplateUrl: string;
  public onStrategyChange: Function;

  public $onInit() {
    this.strategies = DeploymentStrategyRegistry.listStrategies(
      this.command.selectedProvider || this.command.cloudProvider,
    );
    this.selectStrategy();
  }

  public selectStrategy(): void {
    const oldStrategy = DeploymentStrategyRegistry.getStrategy(this.currentStrategy);
    const newStrategy = DeploymentStrategyRegistry.getStrategy(this.command.strategy);

    if (oldStrategy && oldStrategy.additionalFields) {
      oldStrategy.additionalFields.forEach(field => {
        if (!newStrategy || !newStrategy.additionalFields || !newStrategy.additionalFields.includes(field)) {
          unset(this.command, field);
        }
      });
    }

    if (newStrategy) {
      this.additionalFieldsTemplateUrl = newStrategy.additionalFieldsTemplateUrl;
      if (newStrategy.initializationMethod) {
        newStrategy.initializationMethod(this.command);
      }
    } else {
      this.additionalFieldsTemplateUrl = null;
    }

    this.currentStrategy = this.command.strategy;

    if (this.onStrategyChange && newStrategy) {
      this.onStrategyChange(this.command, newStrategy);
    }
  }
}

export class DeploymentStrategySelector implements IComponentOptions {
  public bindings = {
    command: '<',
    onStrategyChange: '<',
    labelColumns: '@',
    fieldColumns: '@',
  };
  public controller: any = DeploymentStrategySelectorController;
  public template = `
    <div class="form-group" ng-if="$ctrl.strategies.length">
      <div class="col-md-{{$ctrl.labelColumns}} sm-label-right" style="padding-left: 13px">
        Strategy
        <help-field key="core.serverGroup.strategy"></help-field>
      </div>
      <div class="col-md-{{$ctrl.fieldColumns}}">
        <ui-select ng-model="$ctrl.command.strategy" class="form-control input-sm" ng-change="$ctrl.selectStrategy()">
          <ui-select-match placeholder="None">{{$select.selected.label}}</ui-select-match>
          <ui-select-choices repeat="strategy.key as strategy in $ctrl.strategies | filter: $select.search">
            <strong ng-bind-html="strategy.label | highlight: $select.search"></strong>
            <div ng-bind-html="strategy.description"></div>
          </ui-select-choices>
        </ui-select>
      </div>
      <div ng-if="$ctrl.additionalFieldsTemplateUrl" class="col-md-9 col-md-offset-3">
        <div ng-include src="$ctrl.additionalFieldsTemplateUrl"></div>
      </div>
    </div>
  `;
}

export class DeploymentStrategySelectorWrapper implements IComponentOptions {
  public bindings: any = {
    command: '<',
    onStrategyChange: '<',
    labelColumns: '<',
    fieldColumns: '<',
  };
  public template = `
    <deployment-strategy-selector
      command="$ctrl.command"
      on-strategy-change="$ctrl.onStrategyChange"
      label-columns="{{!$ctrl.labelColumns ? '3' : $ctrl.labelColumns}}"
      field-columns="{{!$ctrl.fieldColumns ? '6' : $ctrl.fieldColumns}}"
    ></deployment-strategy-selector>
  `;
}

export const DEPLOYMENT_STRATEGY_SELECTOR_COMPONENT = 'spinnaker.core.deploymentStrategy.deploymentStrategySelector';
module(DEPLOYMENT_STRATEGY_SELECTOR_COMPONENT, [])
  .component('deploymentStrategySelector', new DeploymentStrategySelector())
  .component('deploymentStrategySelectorWrapper', new DeploymentStrategySelectorWrapper());
