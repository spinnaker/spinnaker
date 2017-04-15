import {IComponentController, ISCEService, module} from 'angular';

import {AUTHENTICATION_SERVICE, AuthenticationService} from 'core/authentication/authentication.service';
import {NetflixSettings} from '../../netflix.settings';
import {Application} from 'core/application/application.model';

import '../tableau.less';

class ApplicationTableauController implements IComponentController {

  public srcUrl: string;

  static get $inject(): string[] {
    return ['$sce', 'app', 'authenticationService'];
  }

  constructor(private $sce: ISCEService,
              private app: Application,
              private authenticationService: AuthenticationService) {

    const user: string[] = this.authenticationService.getAuthenticatedUser().name.split('@');
    const url = NetflixSettings.tableau.appSourceUrl.replace('${app}', this.app.name).replace('${user}', user[0]);
    this.srcUrl = this.$sce.trustAsResourceUrl(url);
  }
}

export const APPLICATION_TABLEAU_CONTROLLER = 'spinnaker.netflix.application.tableau.controller';
module(APPLICATION_TABLEAU_CONTROLLER, [AUTHENTICATION_SERVICE])
  .controller('AppTableauCtrl', ApplicationTableauController);
