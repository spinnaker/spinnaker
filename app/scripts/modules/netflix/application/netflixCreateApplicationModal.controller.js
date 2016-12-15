'use strict';

import _ from 'lodash';
import {PAGER_DUTY_SELECT_FIELD_COMPONENT} from '../pagerDuty/pagerDutySelectField.component';
import {APPLICATION_READ_SERVICE} from 'core/application/service/application.read.service';
import {APPLICATION_WRITE_SERVICE} from 'core/application/service/application.write.service';
import {ACCOUNT_SERVICE} from 'core/account/account.service';

module.exports = angular
  .module('spinnaker.netflix.application.create.modal.controller', [
    require('angular-ui-router'),
    APPLICATION_WRITE_SERVICE,
    APPLICATION_READ_SERVICE,
    ACCOUNT_SERVICE,
    require('core/config/settings.js'),
    PAGER_DUTY_SELECT_FIELD_COMPONENT,
  ])
  .controller('netflixCreateApplicationModalCtrl', function($controller, $scope, $q, $log, $state, $uibModalInstance,
                                                            settings,
                                                            accountService, applicationWriter, applicationReader) {

    angular.extend(this, $controller('CreateApplicationModalCtrl', {
      $scope: $scope,
      $q: $q,
      $log: $log,
      $state: $state,
      $uibModalInstance: $uibModalInstance,
      accountService: accountService,
      applicationWriter: applicationWriter,
      applicationReader: applicationReader,
      _ : _,
    }));

    this.chaosEnabled = settings.feature.chaosMonkey;

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

  });
