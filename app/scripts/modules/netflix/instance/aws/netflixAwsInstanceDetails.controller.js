'use strict';

const angular = require('angular');
import { find, uniq } from 'lodash';

import {
  ACCOUNT_SERVICE,
  CONFIRMATION_MODAL_SERVICE,
  INSTANCE_READ_SERVICE,
  INSTANCE_WRITE_SERVICE,
  RECENT_HISTORY_SERVICE
} from '@spinnaker/core';

module.exports = angular.module('spinnaker.netflix.instance.aws.controller', [
  require('angular-ui-router').default,
  require('angular-ui-bootstrap'),
  ACCOUNT_SERVICE,
  INSTANCE_WRITE_SERVICE,
  INSTANCE_READ_SERVICE,
  CONFIRMATION_MODAL_SERVICE,
  RECENT_HISTORY_SERVICE,
  require('amazon/instance/details/instance.details.controller.js'),
])
  .controller('netflixAwsInstanceDetailsCtrl', function ($scope, $state, $uibModal,
                                                         instanceWriter, confirmationModalService, recentHistoryService,
                                                         accountService,
                                                         instanceReader, instance, app, $q, $controller) {

    this.instanceDetailsLoaded = () => {
      this.getBastionAddressForAccount($scope.instance.account);
      var discoveryMetric = find($scope.healthMetrics, function(metric) { return metric.type === 'Discovery'; });
      if (discoveryMetric && discoveryMetric.vipAddress) {
        var vipList = discoveryMetric.vipAddress;
        let vipAddress = vipList.includes(',') ? vipList.split(',') : [vipList];
        $scope.instance.vipAddress = uniq(vipAddress);
      }
      if (discoveryMetric && discoveryMetric.secureVipAddress) {
        var secureVipList = discoveryMetric.secureVipAddress;
        let secureVipAddress = secureVipList.includes(',') ? secureVipList.split(',') : [secureVipList];
        $scope.instance.secureVipAddress = uniq(secureVipAddress);
      }
    };

    angular.extend(this, $controller('awsInstanceDetailsCtrl', {
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

    this.getBastionAddressForAccount = (account) => {
      return accountService.getAccountDetails(account).then((details) => {
        this.bastionHost = details.bastionHost || 'unknown';
        $scope.sshLink = `ssh -t ${this.bastionHost} 'oq-ssh --region ${$scope.instance.region} ${$scope.instance.instanceId}'`;
      });
    };

  }
);
