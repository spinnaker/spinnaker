import { IController, IScope, module } from 'angular';

import { IModalInstanceService } from 'angular-ui-bootstrap';
import { react2angular } from 'react2angular';

import { Application, SERVER_GROUP_WRITER, TaskMonitor, ServerGroupWriter, IServerGroupCommand } from '@spinnaker/core';

import { CloudFoundryCreateServerGroup } from './createServerGroup.cf';
import { ICloudFoundryCreateServerGroupCommand } from '../serverGroupConfigurationModel.cf';

class CloudFoundryCreateServerGroupCtrl implements IController {
  public taskMonitor: TaskMonitor;

  constructor(
    public $scope: IScope,
    private $uibModalInstance: IModalInstanceService,
    private serverGroupCommand: ICloudFoundryCreateServerGroupCommand,
    private application: Application,
    private serverGroupWriter: ServerGroupWriter,
  ) {
    'ngInject';
    this.$scope.application = this.application;
    this.$scope.command = this.serverGroupCommand;
    this.$scope.serverGroupWriter = this.serverGroupWriter;
    this.taskMonitor = new TaskMonitor({
      application: this.application,
      title: 'Creating your server group',
      modalInstance: this.$uibModalInstance,
    });
  }

  public cancel = () => this.$uibModalInstance.dismiss();
  public submit = (command?: IServerGroupCommand) => this.$uibModalInstance.close(command);
}

export const CLOUD_FOUNDRY_CREATE_SERVER_GROUP = 'spinnaker.cloudfoundry.createServerGroup.controller';
module(CLOUD_FOUNDRY_CREATE_SERVER_GROUP, [SERVER_GROUP_WRITER])
  .component(
    'cfCreateServerGroup',
    react2angular(CloudFoundryCreateServerGroup, [
      'onDismiss',
      'onSubmit',
      'initialCommand',
      'taskMonitor',
      'serverGroupWriter',
      'application',
    ]),
  )
  .controller('cfCreateServerGroupCtrl', CloudFoundryCreateServerGroupCtrl);
