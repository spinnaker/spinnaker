'use strict';

import _ from 'lodash';
import {ACCOUNT_SERVICE} from 'core/account/account.service';

let angular = require('angular');
import PagerDutyFieldModule from '../pagerDuty/pagerDutySelectField.component';
import PagerDutyTagModule from '../pagerDuty/pagerDutyTag.component';

module.exports = angular
  .module('spinnaker.netflix.application.edit.modal.controller', [
    require('angular-ui-router'),
    require('core/application/service/applications.write.service.js'),
    ACCOUNT_SERVICE,
    PagerDutyFieldModule,
    PagerDutyTagModule
  ])
  .controller('netflixEditApplicationController', function($controller, $window, $state, $uibModalInstance, application, applicationWriter,
                                                            accountService) {

    if (application.attributes.legacyUdf === undefined) {
      application.attributes.legacyUdf = true;
    }
    angular.extend(this, $controller('EditApplicationController', {
      $window: $window,
      $state: $state,
      $uibModalInstance: $uibModalInstance,
      application: application,
      applicationWriter: applicationWriter,
      _ : _,
      accountService: accountService,
    }));

  });
