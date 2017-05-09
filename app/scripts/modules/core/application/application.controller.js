'use strict';

let angular = require('angular');

import {RECENT_HISTORY_SERVICE} from 'core/history/recentHistory.service';

require('./newapplication.less');
require('./application.less');

module.exports = angular.module('spinnaker.application.controller', [
  require('angular-ui-router').default,
  RECENT_HISTORY_SERVICE,
  require('../presentation/refresher/componentRefresher.directive.js'),
])
  .controller('ApplicationCtrl', function($scope, $state, app, recentHistoryService, $uibModal) {
    $scope.application = app;
    $scope.insightTarget = app;
    $scope.refreshTooltipTemplate = require('./applicationRefresh.tooltip.html');
    if (app.notFound) {
      recentHistoryService.removeLastItem('applications');
      return;
    }

    app.enableAutoRefresh($scope);

    this.pageApplicationOwner = () => {
      $uibModal.open({
        templateUrl: require('./modal/pageApplicationOwner.html'),
        controller: 'PageApplicationOwner as ctrl',
        resolve: {
          application: () => $scope.application
        }
      });
    };
  }
);
