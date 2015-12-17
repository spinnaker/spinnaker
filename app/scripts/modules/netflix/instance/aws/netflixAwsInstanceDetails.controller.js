'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.netflix.instance.aws.controller', [
  require('angular-ui-router'),
  require('angular-ui-bootstrap'),
  require('../../../core/utils/lodash.js'),
  require('../../../core/account/account.service.js'),
  require('../../../core/instance/instance.write.service.js'),
  require('../../../core/instance/instance.read.service.js'),
  require('../../../core/confirmationModal/confirmationModal.service.js'),
  require('../../../core/insight/insightFilterState.model.js'),
  require('../../../core/history/recentHistory.service.js'),
  require('../../../core/utils/selectOnDblClick.directive.js'),
  require('../../../core/config/settings.js'),
  require('../../../amazon/instance/details/instance.details.controller.js'),
])
  .controller('netflixAwsInstanceDetailsCtrl', function ($scope, $state, $uibModal, InsightFilterStateModel, settings,
                                                         instanceWriter, confirmationModalService, recentHistoryService,
                                                         accountService,
                                                         instanceReader, _, instance, app, $q, $controller) {

    this.instanceDetailsLoaded = () => {
      this.getBastionAddressForAccount($scope.instance.account);
      var discoveryMetric = _.find($scope.healthMetrics, function(metric) { return metric.type === 'Discovery'; });
      if (discoveryMetric && discoveryMetric.vipAddress) {
        var vipList = discoveryMetric.vipAddress;
        let vipAddress = vipList.contains(',') ? vipList.split(',') : [vipList];
        $scope.instance.vipAddress = _.uniq(vipAddress);
      }
      if (discoveryMetric && discoveryMetric.secureVipAddress) {
        var secureVipList = discoveryMetric.secureVipAddress;
        let secureVipAddress = secureVipList.contains(',') ? secureVipList.split(',') : [secureVipList];
        $scope.instance.secureVipAddress = _.uniq(secureVipAddress);
      }
    };

    angular.extend(this, $controller('awsInstanceDetailsCtrl', {
      $scope: $scope,
      $state: $state,
      $uibModal: $uibModal,
      InsightFilterStateModel: InsightFilterStateModel,
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
