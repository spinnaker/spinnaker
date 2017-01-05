'use strict';

import {CHAP_SERVICE} from './chap.service';

const angular = require('angular');

module.exports = angular.module('spinnaker.netflix.pipeline.stage.chap.controller', [
  CHAP_SERVICE,
]).controller('ChapStageCtrl', function ($scope, stage, chapService) {
    $scope.stage = stage;
    this.viewState = {
      testCasesLoaded: false,
    };
    chapService.listTestCases().then(testCases => {
      this.testCases = testCases;
      this.viewState.testCasesLoaded = true;
    });

  });
