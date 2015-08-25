'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.insight.controller', [
  require('angular-ui-router'),
  require('./insightFilterState.model.js'),
])
  .controller('InsightCtrl', function($scope, InsightFilterStateModel) {

    $scope.InsightFilterStateModel = InsightFilterStateModel;

  }).name;
