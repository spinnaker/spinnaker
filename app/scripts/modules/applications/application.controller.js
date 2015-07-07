'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.application.controller', [])
  .controller('ApplicationCtrl', function($scope, app) {
    $scope.application = app;
    $scope.insightTarget = app;
    if (app.notFound) {
      return;
    }

    this.toggleRefresh = function() {
      if (app.autoRefreshEnabled) {
        app.disableAutoRefresh();
      } else {
        app.resumeAutoRefresh();
        app.refreshImmediately();
      }
    };

    app.enableAutoRefresh($scope);
  }
).name;

