import { IController, module } from 'angular';

import { IServerGroupCommand } from 'core/serverGroup';
import { $rootScope } from 'ngimport';

export class DeploymentStrategyFieldsController implements IController {
  public command: IServerGroupCommand;

  constructor(command: IServerGroupCommand) {
    this.command = command;
  }

  public $onChanges() {
    $rootScope.$apply();
  }

  public test() {
    return true;
  }
}

export const DEPLOYMENT_STRATEGY_FIELDS_CONTROLLER = 'spinnaker.core.deploymentStrategy.controller';
module(DEPLOYMENT_STRATEGY_FIELDS_CONTROLLER, []).controller(
  'deploymentStrategyFieldsController',
  DeploymentStrategyFieldsController,
);
