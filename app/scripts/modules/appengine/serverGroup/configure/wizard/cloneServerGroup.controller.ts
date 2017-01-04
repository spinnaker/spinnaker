import {copy, module} from 'angular';

import {Application} from 'core/application/application.model';
import {IAppengineServerGroupCommand, AppengineServerGroupCommandBuilder} from '../serverGroupCommandBuilder.service';

import './serverGroupWizard.less';

class AppengineCloneServerGroupCtrl {
  public pages: { [pageKey: string]: string } = {
    'basicSettings': require('./basicSettings.html'),
    'advancedSettings': require('./advancedSettings.html'),
  };
  public state = { loading: true };
  public taskMonitor: any;

  static get $inject() { return ['$scope',
                                 '$uibModalInstance',
                                 'title',
                                 'serverGroupCommand',
                                 'application',
                                 'taskMonitorService',
                                 'serverGroupWriter',
                                 'appengineServerGroupCommandBuilder']; }

  constructor(public $scope: any,
              private $uibModalInstance: any,
              private title: string,
              public serverGroupCommand: IAppengineServerGroupCommand,
              private application: Application,
              private taskMonitorService: any,
              private serverGroupWriter: any,
              private commandBuilder: AppengineServerGroupCommandBuilder) {

    if (serverGroupCommand) {
      $scope.command = serverGroupCommand;
      this.state.loading = false;
    } else {
      commandBuilder.buildNewServerGroupCommand(application, 'appengine', 'createPipeline')
        .then((command) => {
          $scope.command = command;
          this.state.loading = false;
        });
    }

    $scope.application = application;

    this.taskMonitor = taskMonitorService.buildTaskMonitor({
      application: this.application,
      title: 'Creating your server group',
      forceRefreshMessage: 'Getting your new server group from App Engine...',
      modalInstance: this.$uibModalInstance,
      forceRefreshEnabled: true
    });
  }

  public cancel(): void {
    this.$uibModalInstance.dismiss();
  }

  public submit(): ng.IPromise<any> {
    let mode = this.$scope.command.viewState.mode;
    if (['editPipeline', 'createPipeline'].includes(mode)) {
      return this.$uibModalInstance.close(this.$scope.command);
    }

    let submitMethod = () => this.serverGroupWriter.cloneServerGroup(copy(this.$scope.command), this.$scope.application);
    this.taskMonitor.submit(submitMethod);
    return null;
  }
}

export const APPENGINE_CLONE_SERVER_GROUP_CTRL = 'spinnaker.appengine.cloneServerGroup.controller';

module(APPENGINE_CLONE_SERVER_GROUP_CTRL, [
    require('core/serverGroup/serverGroup.write.service.js'),
  ]).controller('appengineCloneServerGroupCtrl', AppengineCloneServerGroupCtrl);
