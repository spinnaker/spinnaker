'use strict';

import {INSIGHT_FILTER_STATE_MODEL} from 'core/insight/insightFilterState.model';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.insight.controller', [
  require('angular-ui-router'),
  INSIGHT_FILTER_STATE_MODEL,
])
  .controller('InsightCtrl', function($scope, InsightFilterStateModel) {

    $scope.InsightFilterStateModel = InsightFilterStateModel;

  });
