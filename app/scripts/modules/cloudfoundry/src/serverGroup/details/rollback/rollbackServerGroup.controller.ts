'use strict';

import { IController, IScope, module } from 'angular';

import { IModalInstanceService } from 'angular-ui-bootstrap';

import { Application, SERVER_GROUP_WRITER, ServerGroupWriter, TaskMonitor, IServerGroupJob } from '@spinnaker/core';

import { ICloudFoundryServerGroup } from 'cloudfoundry/domain';

interface ICloudFoundryRollbackServerGroupCommand extends IServerGroupJob {
  rollbackType: string;
  foo?: string;
  rollbackContext: {
    rollbackServerGroupName: string;
    restoreServerGroupName: string;
    targetHealthyRollbackPercentage: number;
  };
}

class CloudfoundryRollbackServerGroupCtrl implements IController {
  public taskMonitor: TaskMonitor;
  private command: ICloudFoundryRollbackServerGroupCommand;

  constructor(
    public $scope: IScope,
    private $uibModalInstance: IModalInstanceService,
    private application: Application,
    private serverGroup: ICloudFoundryServerGroup,
    private previousServerGroup: ICloudFoundryServerGroup,
    private allServerGroups: ICloudFoundryServerGroup[],
    private disabledServerGroups: ICloudFoundryServerGroup[],
    private serverGroupWriter: ServerGroupWriter,
  ) {
    'ngInject';
    this.initialize();
  }

  private initialize(): void {
    this.command = {
      rollbackType: 'EXPLICIT',
      rollbackContext: {
        rollbackServerGroupName: this.serverGroup.name,
        restoreServerGroupName: this.previousServerGroup ? this.previousServerGroup.name : undefined,
        targetHealthyRollbackPercentage: 100,
      },
    };
    this.disabledServerGroups = this.disabledServerGroups.sort((a, b) => b.name.localeCompare(a.name));
    this.allServerGroups = this.allServerGroups.sort((a, b) => b.name.localeCompare(a.name));
    this.taskMonitor = new TaskMonitor({
      application: this.application,
      title: 'Rollback ' + this.serverGroup.name,
      modalInstance: this.$uibModalInstance,
    });
  }

  public label = function(serverGroup: ICloudFoundryServerGroup) {
    if (!serverGroup) {
      return '';
    }

    if (!serverGroup.buildInfo || !serverGroup.buildInfo.jenkins || !serverGroup.buildInfo.jenkins.number) {
      return serverGroup.name;
    }

    return serverGroup.name + ' (build #' + serverGroup.buildInfo.jenkins.number + ')';
  };

  public isValid = function() {
    return this.command.rollbackContext.restoreServerGroupName !== undefined;
  };

  public submit(): void {
    if (!this.isValid()) {
      return;
    }
    const submitMethod = () =>
      this.serverGroupWriter.rollbackServerGroup(this.serverGroup, this.application, this.command);
    this.taskMonitor.submit(submitMethod);
  }

  public cancel(): void {
    this.$uibModalInstance.dismiss();
  }
}

export const CLOUD_FOUNDRY_ROLLBACK_SERVER_GROUP_CTRL =
  'spinnaker.cloudfoundry.serverGroup.details.rollback.controller';
module(CLOUD_FOUNDRY_ROLLBACK_SERVER_GROUP_CTRL, [SERVER_GROUP_WRITER]).controller(
  'cloudfoundryRollbackServerGroupCtrl',
  CloudfoundryRollbackServerGroupCtrl,
);
