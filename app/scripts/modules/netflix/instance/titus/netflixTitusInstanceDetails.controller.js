'use strict';

import _ from 'lodash';

import {ACCOUNT_SERVICE} from 'core/account/account.service';
import {CONFIRMATION_MODAL_SERVICE} from 'core/confirmationModal/confirmationModal.service';
import {INSTANCE_READ_SERVICE} from 'core/instance/instance.read.service';
import {INSTANCE_WRITE_SERVICE} from 'core/instance/instance.write.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.netflix.instance.titus.controller', [
  require('angular-ui-router'),
  require('angular-ui-bootstrap'),
  ACCOUNT_SERVICE,
  INSTANCE_WRITE_SERVICE,
  INSTANCE_READ_SERVICE,
  CONFIRMATION_MODAL_SERVICE,
  require('core/history/recentHistory.service.js'),
  require('core/utils/selectOnDblClick.directive.js'),
  require('core/config/settings.js'),
  require('../../../titus/instance/details/instance.details.controller.js'),
])
  .controller('netflixTitusInstanceDetailsCtrl', function ($scope, $state, $uibModal, settings,
                                                         instanceWriter, confirmationModalService, recentHistoryService,
                                                         accountService, instanceReader, instance, app, $q, $controller) {

    this.instanceDetailsLoaded = () => {
      this.getBastionAddressForAccount($scope.instance.account, $scope.instance.region);
    };

    angular.extend(this, $controller('titusInstanceDetailsCtrl', {
      $scope: $scope,
      $state: $state,
      $uibModal: $uibModal,
      settings: settings,
      instanceWriter: instanceWriter,
      confirmationModalService: confirmationModalService,
      recentHistoryService: recentHistoryService,
      instanceReader: instanceReader,
      _: _,
      instance: instance,
      app: app,
      $q: $q,
      overrides: {
        instanceDetailsLoaded: this.instanceDetailsLoaded,
      }
    }));

    this.getBastionAddressForAccount = (account, region) => {
      return accountService.getAccountDetails(account).then((details) => {
        this.bastionHost = details.bastionHost || 'unknown';
        this.apiEndpoint = _.filter(details.regions, {name: region})[0].endpoint;
        this.titusUiEndpoint = this.apiEndpoint.replace('titusapi', 'titus-ui').replace('http', 'https').replace('7101', '7001');
        if(region != 'us-east-1') {
          this.bastionStack = '-stack ' + this.apiEndpoint.split('.' + region)[0].replace('http://titusapi.', '');
        } else {
          this.bastionStack = '';
        }
      });
    };

  }
);
