'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.pipeline.stage.manualJudgment.service', [
    require('../../../../config/settings.js'),
    require('../../../../delivery/service/execution.service.js'),
  ])
  .factory('manualJudgmentService', function($http, $q, settings, executionService) {

    let buildMatcher = (stage, judgment, deferred) => {
      return (execution) => {
        let matches = execution.stages.filter((test) => test.id === stage.id);
        if (!matches.length) {
          deferred.reject();
          return true;
        }
        return matches[0].status !== 'RUNNING';
      };
    };

    let provideJudgment = (execution, stage, judgment) => {
      var targetUrl = [settings.gateUrl, 'pipelines', execution.id, 'stages', stage.id].join('/');
      var deferred = $q.defer();
      var request = {
        method: 'PATCH',
        url: targetUrl,
        data: {judgmentStatus: judgment},
        timeout: settings.pollSchedule * 2 + 5000, // TODO: replace with apiHost call
      };

      $http(request)
        .success(() => {
          executionService.waitUntilExecutionMatches(execution.id, buildMatcher(stage, judgment, deferred))
            .then(deferred.resolve, deferred.reject);
          }
        )
        .error(deferred.reject);

      return deferred.promise;
    };

    return {
      provideJudgment: provideJudgment
    };
  }).name;
