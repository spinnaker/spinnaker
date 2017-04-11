import {copy, module} from 'angular';
import {merge, get} from 'lodash';

import {Application} from 'core/application/application.model';
import {SERVER_GROUP_WRITER, ServerGroupWriter} from 'core/serverGroup/serverGroupWriter.service';
import {IAppengineServerGroupCommand, AppengineServerGroupCommandBuilder} from '../serverGroupCommandBuilder.service';
import {TASK_MONITOR_BUILDER, TaskMonitorBuilder, TaskMonitor} from 'core/task/monitor/taskMonitor.builder';
import {APPENGINE_DYNAMIC_BRANCH_LABEL} from './dynamicBranchLabel.component';
import {APPENGINE_CONFIG_FILE_CONFIGURER} from './configFiles.component';
import {AppengineHealth} from 'appengine/common/appengineHealth';

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

  static get $inject() { return ['$scope',
                                 '$uibModalInstance',
                                 'serverGroupCommand',
                                 'application',
                                 'taskMonitorBuilder',
                                 'serverGroupWriter',
                                 'appengineServerGroupCommandBuilder']; }

  constructor(public $scope: any,
              private $uibModalInstance: any,
              public serverGroupCommand: IAppengineServerGroupCommand,
              private application: Application,
              private taskMonitorBuilder: TaskMonitorBuilder,
              private serverGroupWriter: ServerGroupWriter,
              commandBuilder: AppengineServerGroupCommandBuilder) {
    if (['create', 'clone', 'editPipeline'].includes(get<string>(serverGroupCommand, 'viewState.mode'))) {
      $scope.command = serverGroupCommand;
      this.state.loading = false;
      this.initialize();
    } else {
      commandBuilder.buildNewServerGroupCommand(application, 'appengine', 'createPipeline')
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

  public submit(): ng.IPromise<any> {
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
