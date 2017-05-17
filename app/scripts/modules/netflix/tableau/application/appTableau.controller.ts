import { IComponentController, ISCEService, module } from 'angular';

import { Application, AUTHENTICATION_SERVICE, AuthenticationService } from '@spinnaker/core';

import { NetflixSettings } from 'netflix/netflix.settings';

import '../tableau.less';

class ApplicationTableauController implements IComponentController {

  public srcUrl: string;

  constructor(private $sce: ISCEService,
              private app: Application,
              private authenticationService: AuthenticationService) {
    'ngInject';

    const user: string[] = this.authenticationService.getAuthenticatedUser().name.split('@');
    const url = NetflixSettings.tableau.appSourceUrl.replace('${app}', this.app.name).replace('${user}', user[0]);
    this.srcUrl = this.$sce.trustAsResourceUrl(url);
  }
}

export const APPLICATION_TABLEAU_CONTROLLER = 'spinnaker.netflix.application.tableau.controller';
module(APPLICATION_TABLEAU_CONTROLLER, [AUTHENTICATION_SERVICE])
  .controller('AppTableauCtrl', ApplicationTableauController);
