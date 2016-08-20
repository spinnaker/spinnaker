'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.pipeline.stage.manualJudgment.service', [
    require('../../../../config/settings.js'),
    require('../../../../delivery/service/execution.service.js'),
  ])
  .factory('manualJudgmentService', function($http, $q, settings, executionService) {

    let provideJudgment = (execution, stage, judgment, input) => {
      let matcher = (execution) => {
        let [match] = execution.stages.filter((test) => test.id === stage.id);
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
