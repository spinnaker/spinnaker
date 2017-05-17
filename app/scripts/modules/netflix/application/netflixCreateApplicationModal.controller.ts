import { extend, IControllerService, ILogService, IQService, IScope, module } from 'angular';
import { IModalInstanceService } from 'angular-ui-bootstrap';
import { StateDeclaration } from 'angular-ui-router';

import {
  ACCOUNT_SERVICE,
  AccountService,
  Application,
  APPLICATION_READ_SERVICE,
  APPLICATION_WRITE_SERVICE,
  ApplicationReader,
  ApplicationWriter,
  SETTINGS
} from '@spinnaker/core';

import { PAGER_DUTY_SELECT_FIELD_COMPONENT } from '../pagerDuty/pagerDutySelectField.component';

class NetflixCreateApplicationModalController {

  public application: Application;
  public chaosEnabled: boolean;

  constructor($q: IQService,
              $log: ILogService,
              $scope: IScope,
              $controller: IControllerService,
              $state: StateDeclaration,
              $uibModalInstance: IModalInstanceService,
              accountService: AccountService,
              applicationReader: ApplicationReader,
              applicationWriter: ApplicationWriter) {
    'ngInject';

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
  require('angular-ui-router').default,
  APPLICATION_WRITE_SERVICE,
  APPLICATION_READ_SERVICE,
  ACCOUNT_SERVICE,
  PAGER_DUTY_SELECT_FIELD_COMPONENT,
])
  .controller('netflixCreateApplicationModalCtrl', NetflixCreateApplicationModalController);
