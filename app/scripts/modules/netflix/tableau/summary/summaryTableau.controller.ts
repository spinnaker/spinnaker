import {ISCEService, module} from 'angular';

import {AUTHENTICATION_SERVICE, AuthenticationService} from 'core/authentication/authentication.service';
import {NetflixSettings} from '../../netflix.settings';

import '../tableau.less';

class SummaryTableauController {

  public srcUrl: string;

  static get $inject(): string[] {
    return ['$sce', 'app', 'authenticationService'];
  }

  constructor(private $sce: ISCEService,
              private authenticationService: AuthenticationService) {
    const user: string[] = this.authenticationService.getAuthenticatedUser().name.split('@');
    const url: string = NetflixSettings.tableau.summarySourceUrl.replace('${user}', user[0]);
    this.srcUrl = this.$sce.trustAsResourceUrl(url);
  }
}

export const SUMMARY_TABLEAU_CONTROLLER = 'spinnaker.netflix.summary.tableau.controller';
module(SUMMARY_TABLEAU_CONTROLLER, [AUTHENTICATION_SERVICE])
  .controller('SummaryTableauCtrl', SummaryTableauController);
