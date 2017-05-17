'use strict';

const angular = require('angular');
import { filter } from 'lodash';

import {
  ACCOUNT_SERVICE,
  CONFIRMATION_MODAL_SERVICE,
  INSTANCE_READ_SERVICE,
  INSTANCE_WRITE_SERVICE,
  RECENT_HISTORY_SERVICE
} from '@spinnaker/core';

module.exports = angular.module('spinnaker.netflix.instance.titus.controller', [
  require('angular-ui-router').default,
  require('angular-ui-bootstrap'),
  ACCOUNT_SERVICE,
  INSTANCE_WRITE_SERVICE,
  INSTANCE_READ_SERVICE,
  CONFIRMATION_MODAL_SERVICE,
  RECENT_HISTORY_SERVICE,
  require('titus/instance/details/instance.details.controller.js'),
])
  .controller('netflixTitusInstanceDetailsCtrl', function ($scope, $state, $uibModal,
                                                         instanceWriter, confirmationModalService, recentHistoryService,
                                                         accountService, instanceReader, instance, app, $q, $controller) {

    this.instanceDetailsLoaded = () => {
      this.getBastionAddressForAccount($scope.instance.account, $scope.instance.region);
    };

    this.hasPorts = () => {
      return Object.keys($scope.instance.resources.ports).length > 0;
    };

    angular.extend(this, $controller('titusInstanceDetailsCtrl', {
      $scope: $scope,
      $state: $state,
      $uibModal: $uibModal,
      instanceWriter: instanceWriter,
      confirmationModalService: confirmationModalService,
      recentHistoryService: recentHistoryService,
      instanceReader: instanceReader,
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
        this.apiEndpoint = filter(details.regions, {name: region})[0].endpoint;
        this.titusUiEndpoint = this.apiEndpoint.replace('titusapi', 'titus-ui').replace('http', 'https').replace('7101', '7001');
        if(region != 'us-east-1') {
          this.bastionStack = '-stack ' + this.apiEndpoint.split('.' + region)[0].replace('http://titusapi.', '');
        } else {
          this.bastionStack = '';
        }

        $scope.sshLink = `ssh -t ${this.bastionHost} 'titus-ssh ${this.bastionStack} -region ${$scope.instance.region} -id ${$scope.instance.id}'`;
      });
    };

  }
);
