'use strict';

import {EXECUTION_SERVICE} from 'core/delivery/service/execution.service';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.pipeline.stage.manualJudgment.service', [
    EXECUTION_SERVICE,
  ])
  .factory('manualJudgmentService', function($http, $q, executionService) {

    let provideJudgment = (execution, stage, judgment, input) => {
      let matcher = (execution) => {
        let match = execution.stages.find((test) => test.id === stage.id);
        return match && match.status !== 'RUNNING';
      };
      let data = {judgmentStatus: judgment, judgmentInput: input};
      return executionService.patchExecution(execution.id, stage.id, data)
        .then(() => executionService.waitUntilExecutionMatches(execution.id, matcher));
    };

    return {
      provideJudgment: provideJudgment
    };
  });
