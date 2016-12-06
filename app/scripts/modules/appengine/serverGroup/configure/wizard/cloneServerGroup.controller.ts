import {copy, module} from 'angular';

import {ServerGroup} from 'core/domain/serverGroup';
import {Application} from 'core/application/application.model';
import {IAppengineServerGroupCommand} from '../serverGroupCommandBuilder.service';

import './serverGroupWizard.less';

class AppengineCloneServerGroupCtrl {
  public pages: { [pageKey: string]: string } = {
    'basicSettings': require('./basicSettings.html'),
    'advancedSettings': require('./advancedSettings.html'),
  };
  public state: { [stateKey: string]: boolean } = {
    loading: false,
  };
  public taskMonitor: any;

  static get $inject() { return ['$scope',
                                 '$uibModalInstance',
                                 'title',
                                 'serverGroup',
                                 'serverGroupCommand',
                                 'application',
                                 'provider',
                                 'taskMonitorService',
                                 'serverGroupWriter']; }

  constructor(public $scope: any,
              private $uibModalInstance: any,
              private title: string,
              private serverGroup: ServerGroup,
              public serverGroupCommand: IAppengineServerGroupCommand,
              private application: Application,
              private provider: string,
              private taskMonitorService: any,
              private serverGroupWriter: any) {
    $scope.command = serverGroupCommand;
    $scope.application = application;

    this.taskMonitor = taskMonitorService.buildTaskMonitor({
      application: this.application,
      title: 'Creating your server group',
      forceRefreshMessage: 'Getting your new server group from Kubernetes...',
      modalInstance: this.$uibModalInstance,
      forceRefreshEnabled: true
    });
  }

  public cancel(): void {
    this.$uibModalInstance.dismiss();
  }

  public submit(): void {
    let submitMethod = () => this.serverGroupWriter.cloneServerGroup(copy(this.$scope.command), this.$scope.application);
    this.taskMonitor.submit(submitMethod);
  }
}

export const APPENGINE_CLONE_SERVER_GROUP_CTRL = 'spinnaker.appengine.cloneServerGroup.controller';

module(APPENGINE_CLONE_SERVER_GROUP_CTRL, [
    require('core/serverGroup/serverGroup.write.service.js'),
  ]).controller('appengineCloneServerGroupCtrl', AppengineCloneServerGroupCtrl);
