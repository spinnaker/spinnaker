import {module, IScope} from 'angular';

import {ServerGroup} from 'core/domain/index';
import {Application} from 'core/application/application.model';

interface IPrivateScope extends IScope {
  $$destroyed: boolean;
}

interface IServerGroupFromStateParams {
  accountId: string;
  region: string;
  name: string;
}

class AppengineServerGroupDetailsController {
  public state = { loading: true };
  public serverGroup: ServerGroup;

  static get $inject () {
    return ['$state',
            '$scope',
            'serverGroup',
            'app',
            'serverGroupReader',
            'InsightFilterStateModel'];
  }

  constructor(private $state: any,
              private $scope: IPrivateScope,
              serverGroup: IServerGroupFromStateParams,
              private app: Application,
              private serverGroupReader: any,
              public InsightFilterStateModel: any) {

    this.serverGroupReader
      .getServerGroup(this.app.name, serverGroup.accountId, serverGroup.region, serverGroup.name)
      .then((serverGroupDetails: ServerGroup) => {
        this.serverGroup = serverGroupDetails;
        this.state.loading = false;
      })
      .catch(() => this.autoClose());
  }

  private autoClose(): void {
    if (this.$scope.$$destroyed) {
      return;
    } else {
      this.$state.params.allowModalToStayOpen = true;
      this.$state.go('^', null, {location: 'replace'});
    }
  }
}

export const APPENGINE_SERVER_GROUP_DETAILS_CONTROLLER = 'spinnaker.appengine.serverGroup.details.controller';

module(APPENGINE_SERVER_GROUP_DETAILS_CONTROLLER, [
    require('core/serverGroup/serverGroup.read.service.js'),
    require('core/insight/insightFilterState.model.js'),
  ])
  .controller('appengineServerGroupDetailsCtrl', AppengineServerGroupDetailsController);
