import { IScope, copy, module } from 'angular';
import { IModalInstanceService } from 'angular-ui-bootstrap';
import { get, merge } from 'lodash';

import {
  Application,
  SERVER_GROUP_WRITER,
  ServerGroupWriter,
  TASK_MONITOR_BUILDER,
  TaskMonitor,
  TaskMonitorBuilder
} from '@spinnaker/core';

import { AppengineHealth } from 'appengine/common/appengineHealth';
import { AppengineServerGroupCommandBuilder, IAppengineServerGroupCommand } from '../serverGroupCommandBuilder.service';
import { APPENGINE_CONFIG_FILE_CONFIGURER } from './configFiles.component';
import { APPENGINE_DYNAMIC_BRANCH_LABEL } from './dynamicBranchLabel.component';

import './serverGroupWizard.less';

class AppengineCloneServerGroupCtrl {
  public pages: {[pageKey: string]: string} = {
    'basicSettings': require('./basicSettings.html'),
    'advancedSettings': require('./advancedSettings.html'),
  };
  public state = {
    loading: true,
  };
  public taskMonitor: TaskMonitor;

  constructor(public $scope: IScope,
              private $uibModalInstance: IModalInstanceService,
              public serverGroupCommand: IAppengineServerGroupCommand,
              private application: Application,
              private taskMonitorBuilder: TaskMonitorBuilder,
              private serverGroupWriter: ServerGroupWriter,
              appengineServerGroupCommandBuilder: AppengineServerGroupCommandBuilder) {
    'ngInject';
    if (['create', 'clone', 'editPipeline'].includes(get<string>(serverGroupCommand, 'viewState.mode'))) {
      $scope.command = serverGroupCommand;
      this.state.loading = false;
      this.initialize();
    } else {
      appengineServerGroupCommandBuilder.buildNewServerGroupCommand(application, 'appengine', 'createPipeline')
        .then((constructedCommand) => {
          $scope.command = merge(constructedCommand, serverGroupCommand);
          this.state.loading = false;
          this.initialize();
        });
    }
  }

  public cancel(): void {
    this.$uibModalInstance.dismiss();
  }

  public submit(): void {
    const mode = this.$scope.command.viewState.mode;
    if (['editPipeline', 'createPipeline'].includes(mode)) {
      return this.$uibModalInstance.close(this.$scope.command);
    } else {
      const command = copy(this.$scope.command);
      // Make sure we're sending off a create operation, because there's no such thing as clone for App Engine.
      command.viewState.mode = 'create';
      const submitMethod = () => this.serverGroupWriter.cloneServerGroup(command, this.$scope.application);
      this.taskMonitor.submit(submitMethod);

      return null;
    }
  }

  private initialize(): void {
    this.$scope.application = this.application;
    this.taskMonitor = this.taskMonitorBuilder.buildTaskMonitor({
      application: this.application,
      title: 'Creating your server group',
      modalInstance: this.$uibModalInstance,
    });
    this.$scope.showPlatformHealthOnlyOverride = this.application.attributes.platformHealthOnlyShowOverride;
    this.$scope.platformHealth = AppengineHealth.PLATFORM;
    if (this.application.attributes.platformHealthOnly) {
      this.$scope.command.interestingHealthProviderNames = [AppengineHealth.PLATFORM];
    }
  }
}

export const APPENGINE_CLONE_SERVER_GROUP_CTRL = 'spinnaker.appengine.cloneServerGroup.controller';
module(APPENGINE_CLONE_SERVER_GROUP_CTRL, [
  SERVER_GROUP_WRITER,
  TASK_MONITOR_BUILDER,
  APPENGINE_DYNAMIC_BRANCH_LABEL,
  APPENGINE_CONFIG_FILE_CONFIGURER,
]).controller('appengineCloneServerGroupCtrl', AppengineCloneServerGroupCtrl);
