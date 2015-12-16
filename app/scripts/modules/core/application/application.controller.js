'use strict';

let angular = require('angular');

require('./newapplication.less');
require('./application.less');

module.exports = angular.module('spinnaker.application.controller', [
  require('exports?"cfp.hotkeys"!angular-hotkeys'),
  require('angular-ui-router'),
  require('../history/recentHistory.service.js'),
  require('../overrideRegistry/override.registry.js'),
])
  .controller('ApplicationCtrl', function($scope, $state, hotkeys, app, recentHistoryService, $window, overrideRegistry) {
    this.applicationNavTemplate = overrideRegistry.getTemplate('applicationNavHeader', require('./applicationNav.html'));
    $scope.$window = $window;
    $scope.application = app;
    $scope.insightTarget = app;
    $scope.refreshTooltipTemplate = require('./applicationRefresh.tooltip.html');
    if (app.notFound) {
      recentHistoryService.removeLastItem('applications');
      return;
    }

    $scope.getAgeColor = () => {
      const yellowAge = 2 * 60 * 1000; // 2 minutes
      const redAge = 5 * 60 * 1000; // 5 minutes
      let lastRefresh = app.lastRefresh || 0;
      let age = new Date().getTime() - lastRefresh;

      return age < yellowAge ? 'young' :
             age < redAge ? 'old' : 'ancient';
    };

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
        callback: () => $state.go('home.applications.application.properties')
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
  }
);

