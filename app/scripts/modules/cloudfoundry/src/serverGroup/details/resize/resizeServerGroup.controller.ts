'use strict';

const angular = require('angular');

import { IController, IScope, module } from 'angular';

import { IModalInstanceService } from 'angular-ui-bootstrap';

import {
  Application,
  SERVER_GROUP_WRITER,
  ServerGroupWriter,
  TaskMonitor,
  IServerGroupJob,
  ICapacity,
} from '@spinnaker/core';

import { ICloudFoundryServerGroup } from 'cloudfoundry/domain';

interface ICloudFoundryResizeServerGroupCommand extends IServerGroupJob {
  capacity?: Partial<ICapacity>;
  diskQuota?: number;
  instanceCount?: number;
  memory?: number;
  serverGroupName: string;
  reason?: string;
}

class CloudfoundryResizeServerGroupCtrl implements IController {
  public pages: { [pageKey: string]: string } = {
    resizeCapacity: require('./resizeCapacity.html'),
  };
  public taskMonitor: TaskMonitor;

  constructor(
    public $scope: IScope,
    private $uibModalInstance: IModalInstanceService,
    private application: Application,
    private serverGroup: ICloudFoundryServerGroup,
    private serverGroupWriter: ServerGroupWriter,
  ) {
    'ngInject';
    this.initialize();
  }

  private initialize(): void {
    this.$scope.application = this.application;
    this.$scope.currentSize = {
      instanceCount: this.serverGroup.instances.length,
      memory: this.serverGroup.memory,
      diskQuota: this.serverGroup.diskQuota,
    };
    this.$scope.command = angular.copy(this.$scope.currentSize);
    this.taskMonitor = new TaskMonitor({
      application: this.application,
      title: 'Resizing ' + this.serverGroup.name,
      modalInstance: this.$uibModalInstance,
    });
  }

  public submit(): void {
    const command: ICloudFoundryResizeServerGroupCommand = {
      serverGroupName: this.serverGroup.name,
      capacity: {
        min: this.$scope.command.instanceCount,
        max: this.$scope.command.instanceCount,
        desired: this.$scope.command.instanceCount,
      },
      diskQuota: this.$scope.command.diskQuota,
      memory: this.$scope.command.memory,
      reason: this.$scope.command.reason,
    };

    const submitMethod = () => this.serverGroupWriter.resizeServerGroup(this.serverGroup, this.application, command);
    this.taskMonitor.submit(submitMethod);
  }

  public cancel(): void {
    this.$uibModalInstance.dismiss();
  }
}

export const CLOUD_FOUNDRY_RESIZE_SERVER_GROUP_CTRL = 'spinnaker.cloudfoundry.serverGroup.details.resize.controller';
module(CLOUD_FOUNDRY_RESIZE_SERVER_GROUP_CTRL, [SERVER_GROUP_WRITER]).controller(
  'cloudfoundryResizeServerGroupCtrl',
  CloudfoundryResizeServerGroupCtrl,
);
