'use strict';

let angular = require('angular');

require('./newapplication.less');
require('./application.less');

module.exports = angular.module('spinnaker.application.controller', [
  require('exports?"cfp.hotkeys"!angular-hotkeys'),
  require('angular-ui-router'),
  require('../history/recentHistory.service.js'),
  require('../overrideRegistry/override.registry.js'),
  require('../presentation/refresher/componentRefresher.directive.js'),
])
  .controller('ApplicationCtrl', function($scope, $state, hotkeys, app, recentHistoryService, overrideRegistry,
                                          $uibModal) {
    this.applicationNavTemplate = overrideRegistry.getTemplate('applicationNavHeader', require('./applicationNav.html'));

    $scope.application = app;
    $scope.insightTarget = app;
    $scope.refreshTooltipTemplate = require('./applicationRefresh.tooltip.html');
    if (app.notFound) {
      recentHistoryService.removeLastItem('applications');
      return;
    }

    var hotkeyBind = hotkeys.bindTo($scope);
    var applicationHotkeys = [
      {
        combo: 'ctrl+alt+0',
        description: 'Pipeline Config',
        callback: () => $state.go('home.applications.application.pipelineConfig'),
      },
      {
        combo: 'ctrl+alt+1',
        description: 'Pipelines',
        callback: () => $state.go('home.applications.application.executions'),
      },
      {
        combo: 'ctrl+alt+2',
        description: 'Clusters',
        callback: () => $state.go('home.applications.application.insight.clusters')
      },
      {
        combo: 'ctrl+alt+3',
        description: 'Load Balancer',
        callback: () => $state.go('home.applications.application.insight.loadBalancers')
      },
      {
        combo: 'ctrl+alt+4',
        description: 'Security Groups',
        callback: () => $state.go('home.applications.application.insight.securityGroups')
      },
      {
        combo: 'ctrl+alt+5',
        description: 'Properties',
        callback: () => $state.go('home.applications.application.propInsights.properties')
      },
      {
        combo: 'ctrl+alt+6',
        description: 'Tasks',
        callback: () => $state.go('home.applications.application.tasks')
      },
      {
        combo: 'ctrl+alt+7',
        description: 'Config',
        callback: () => $state.go('home.applications.application.config')
      },
    ];

    applicationHotkeys.forEach(hotkeyBind.add);
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
