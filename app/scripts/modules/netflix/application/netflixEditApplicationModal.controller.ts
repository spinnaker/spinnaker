import {extend, IControllerService, IWindowService, module} from 'angular';
import {IState} from 'angular-ui-router';
import {IModalInstanceService} from 'angular-ui-bootstrap';

import {ACCOUNT_SERVICE, AccountService} from 'core/account/account.service';
import {APPLICATION_WRITE_SERVICE, ApplicationWriter} from 'core/application/service/application.write.service';
import {PAGER_DUTY_SELECT_FIELD_COMPONENT} from '../pagerDuty/pagerDutySelectField.component';
import {PAGER_DUTY_TAG_COMPONENT} from '../pagerDuty/pagerDutyTag.component';
import {UI_SELECT_COMPONENT} from 'core/widgets/uiSelect.component';
import {Application} from 'core/application/application.model';

class NetflixEditApplicationModalController {

  static get $inject(): string[] {
    return [
      '$window', '$controller', '$state', '$uibModalInstance', 'application', 'applicationWriter', 'accountService'
    ];
  }

  constructor($window: IWindowService,
              $controller: IControllerService,
              $state: IState,
              $uibModalInstance: IModalInstanceService,
              application: Application,
              applicationWriter: ApplicationWriter,
              accountService: AccountService) {

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
  require('angular-ui-router'),
  APPLICATION_WRITE_SERVICE,
  ACCOUNT_SERVICE,
  PAGER_DUTY_SELECT_FIELD_COMPONENT,
  PAGER_DUTY_TAG_COMPONENT,
  UI_SELECT_COMPONENT
])
  .controller('netflixEditApplicationController', NetflixEditApplicationModalController);
