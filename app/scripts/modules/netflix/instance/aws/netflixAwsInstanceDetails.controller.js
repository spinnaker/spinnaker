'use strict';

import _ from 'lodash';

import {ACCOUNT_SERVICE} from 'core/account/account.service';
import {CONFIRMATION_MODAL_SERVICE} from 'core/confirmationModal/confirmationModal.service';
import {INSTANCE_READ_SERVICE} from 'core/instance/instance.read.service';
import {INSTANCE_WRITE_SERVICE} from 'core/instance/instance.write.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.netflix.instance.aws.controller', [
  require('angular-ui-router'),
  require('angular-ui-bootstrap'),
  ACCOUNT_SERVICE,
  INSTANCE_WRITE_SERVICE,
  INSTANCE_READ_SERVICE,
  CONFIRMATION_MODAL_SERVICE,
  require('core/history/recentHistory.service.js'),
  require('core/utils/selectOnDblClick.directive.js'),
  require('core/config/settings.js'),
  require('amazon/instance/details/instance.details.controller.js'),
])
  .controller('netflixAwsInstanceDetailsCtrl', function ($scope, $state, $uibModal, settings,
                                                         instanceWriter, confirmationModalService, recentHistoryService,
                                                         accountService,
                                                         instanceReader, instance, app, $q, $controller) {

    this.instanceDetailsLoaded = () => {
      this.getBastionAddressForAccount($scope.instance.account);
      var discoveryMetric = _.find($scope.healthMetrics, function(metric) { return metric.type === 'Discovery'; });
      if (discoveryMetric && discoveryMetric.vipAddress) {
        var vipList = discoveryMetric.vipAddress;
        let vipAddress = vipList.includes(',') ? vipList.split(',') : [vipList];
        $scope.instance.vipAddress = _.uniq(vipAddress);
      }
      if (discoveryMetric && discoveryMetric.secureVipAddress) {
        var secureVipList = discoveryMetric.secureVipAddress;
        let secureVipAddress = secureVipList.includes(',') ? secureVipList.split(',') : [secureVipList];
        $scope.instance.secureVipAddress = _.uniq(secureVipAddress);
      }
    };

    angular.extend(this, $controller('awsInstanceDetailsCtrl', {
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

    this.getBastionAddressForAccount = (account) => {
      return accountService.getAccountDetails(account).then((details) => {
        this.bastionHost = details.bastionHost || 'unknown';
      });
    };

  }
);
