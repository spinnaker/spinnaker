import {extend, IControllerService, ILogService, IQService, IScope, module} from 'angular';
import {IState} from 'angular-ui-router';
import {IModalInstanceService} from 'angular-ui-bootstrap';

import {PAGER_DUTY_SELECT_FIELD_COMPONENT} from '../pagerDuty/pagerDutySelectField.component';
import {APPLICATION_READ_SERVICE, ApplicationReader} from 'core/application/service/application.read.service';
import {APPLICATION_WRITE_SERVICE, ApplicationWriter} from 'core/application/service/application.write.service';
import {ACCOUNT_SERVICE, AccountService} from 'core/account/account.service';
import {UI_SELECT_COMPONENT} from 'core/widgets/uiSelect.component';
import {SETTINGS} from 'core/config/settings';
import {Application} from 'core/application/application.model';

class NetflixCreateApplicationModalController {

  public application: Application;
  public chaosEnabled: boolean;

  static get $inject(): string[] {
    return [
      '$q', '$log', '$scope', '$controller', '$state', '$uibModalInstance',
      'accountService', 'applicationReader', 'applicationWriter'
    ];
  }

  constructor($q: IQService,
              $log: ILogService,
              $scope: IScope,
              $controller: IControllerService,
              $state: IState,
              $uibModalInstance: IModalInstanceService,
              accountService: AccountService,
              applicationReader: ApplicationReader,
              applicationWriter: ApplicationWriter) {

    extend(this, $controller('CreateApplicationModalCtrl', {
      $scope: $scope,
      $q: $q,
      $log: $log,
      $state: $state,
      $uibModalInstance: $uibModalInstance,
      accountService: accountService,
      applicationWriter: applicationWriter,
      applicationReader: applicationReader
    }));

    this.chaosEnabled = SETTINGS.feature.chaosMonkey;

    if (this.chaosEnabled) {
      this.application.chaosMonkey = {
        enabled: true,
        meanTimeBetweenKillsInWorkDays: 2,
        minTimeBetweenKillsInWorkDays: 1,
        grouping: 'cluster',
        regionsAreIndependent: true,
        exceptions: [],
      };
    }

    this.application.legacyUdf = false;
  }
}
export const NETFLIX_CREATE_APPLICATION_MODAL_CONTROLLER = 'spinnaker.netflix.application.create.modal.controller';
module(NETFLIX_CREATE_APPLICATION_MODAL_CONTROLLER, [
  require('angular-ui-router'),
  APPLICATION_WRITE_SERVICE,
  APPLICATION_READ_SERVICE,
  ACCOUNT_SERVICE,
  PAGER_DUTY_SELECT_FIELD_COMPONENT,
  UI_SELECT_COMPONENT
])
  .controller('netflixCreateApplicationModalCtrl', NetflixCreateApplicationModalController);
