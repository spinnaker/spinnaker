import { extend, IControllerService, IWindowService, module } from 'angular';
import { IModalInstanceService } from 'angular-ui-bootstrap';
import { StateObject } from 'angular-ui-router';

import {
  ACCOUNT_SERVICE,
  AccountService,
  Application,
  APPLICATION_WRITE_SERVICE,
  ApplicationWriter
} from '@spinnaker/core';

import { PAGER_DUTY_SELECT_FIELD_COMPONENT } from '../pagerDuty/pagerDutySelectField.component';
import { PAGER_DUTY_TAG_COMPONENT } from '../pagerDuty/pagerDutyTag.component';

class NetflixEditApplicationModalController {
  constructor($window: IWindowService,
              $controller: IControllerService,
              $state: StateObject,
              $uibModalInstance: IModalInstanceService,
              application: Application,
              applicationWriter: ApplicationWriter,
              accountService: AccountService) {
    'ngInject';

    if (application.attributes.legacyUdf === undefined) {
      application.attributes.legacyUdf = true;
    }
    extend(this, $controller('EditApplicationController', {
      $window: $window,
      $state: $state,
      $uibModalInstance: $uibModalInstance,
      application: application,
      applicationWriter: applicationWriter,
      accountService: accountService,
    }));
  }
}

export const NETFLIX_EDIT_APPLICATION_MODAL_CONTROLLER = 'spinnaker.netflix.application.edit.modal.controller';
module(NETFLIX_EDIT_APPLICATION_MODAL_CONTROLLER, [
  require('angular-ui-router').default,
  APPLICATION_WRITE_SERVICE,
  ACCOUNT_SERVICE,
  PAGER_DUTY_SELECT_FIELD_COMPONENT,
  PAGER_DUTY_TAG_COMPONENT,
])
  .controller('netflixEditApplicationController', NetflixEditApplicationModalController);
