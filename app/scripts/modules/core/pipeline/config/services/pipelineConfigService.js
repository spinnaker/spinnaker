'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.services.configService', [
  require('exports?"restangular"!imports?_=lodash!restangular'),
  require('../../../utils/lodash.js'),
  require('../../../authentication/authentication.service.js'),
  require('../../../cache/viewStateCache.js'),
])
  .factory('pipelineConfigService', function (_, $q, Restangular, authenticationService, viewStateCache) {

    var configViewStateCache = viewStateCache.createCache('pipelineConfig', { version: 1 });

    function buildViewStateCacheKey(applicationName, pipelineName) {
      return [applicationName, pipelineName].join(':');
    }

    function getPipelinesForApplication(applicationName) {
      return Restangular.one('applications', applicationName).all('pipelineConfigs').getList().then(function (pipelines) {
        return sortPipelines(pipelines);
      });
    }

    function getStrategiesForApplication(applicationName) {
      return Restangular.one('applications', applicationName).all('strategyConfigs').getList().then(function (pipelines) {
        return sortPipelines(pipelines);
      });
    }

    function sortPipelines(pipelines) {

      var sorted = _.sortByAll(pipelines, ['index', 'name']);

      // if there are pipelines with a bad index, fix that
      var misindexed = [];
      if (sorted && sorted.length) {
        sorted.forEach(function (pipeline, index) {
          if (pipeline.index !== index) {
            pipeline.index = index;
            misindexed.push(savePipeline(pipeline));
          }
        });
        if (misindexed.length) {
          return $q.all(misindexed).then(function () {
            return sorted;
          });
        }
      }
      return sorted;
    }

    function deletePipeline(applicationName, pipeline, pipelineName) {
      return Restangular.all(pipeline.strategy ? 'strategies' : 'pipelines').one(applicationName, pipelineName).remove();
    }

    function savePipeline(pipeline) {
      delete pipeline.isNew;
      pipeline.stages.forEach(function(stage) {
        delete stage.isNew;
        if (!stage.name) {
          delete stage.name;
        }
      });
      return Restangular.all( pipeline.strategy ? 'strategies' : 'pipelines').post(pipeline);
    }

    function renamePipeline(applicationName, pipeline, currentName, newName) {
      configViewStateCache.remove(buildViewStateCacheKey(applicationName, currentName));
      return Restangular.all(pipeline.strategy ? 'strategies' : 'pipelines').all('move').post({
        application: applicationName,
        from: currentName,
        to: newName
      });
    }

    function triggerPipeline(applicationName, pipelineName, body) {
      body = body || {};
      body.user = authenticationService.getAuthenticatedUser().name;
      return Restangular.one('pipelines', applicationName)
        .customPOST(body, pipelineName);
    }

    function getDownstreamStageIds(pipeline, stage) {
      var downstream = [];
      var children = pipeline.stages.filter(function(stageToTest) {
        return stageToTest.requisiteStageRefIds &&
               stageToTest.requisiteStageRefIds.indexOf(stage.refId) !== -1;
      });
      if (children.length) {
        downstream = _.pluck(children, 'refId');
        children.forEach(function(child) {
          downstream = downstream.concat(getDownstreamStageIds(pipeline, child));
        });
      }
      return _(downstream).compact().uniq().value();
    }

    function getDependencyCandidateStages(pipeline, stage) {
      var downstreamIds = getDownstreamStageIds(pipeline, stage);
      return pipeline.stages.filter(function(stageToTest) {
        return stage !== stageToTest &&
          stageToTest.requisiteStageRefIds &&
          downstreamIds.indexOf(stageToTest.refId) === -1 &&
          stage.requisiteStageRefIds.indexOf(stageToTest.refId) === -1;
      });
    }

    function getAllUpstreamDependencies(pipeline, stage) {
      var upstreamStages = [];
      if (stage.requisiteStageRefIds && stage.requisiteStageRefIds.length) {
        pipeline.stages.forEach(function(stageToTest) {
          if (stage.requisiteStageRefIds.indexOf(stageToTest.refId) !== -1) {
            upstreamStages.push(stageToTest);
            upstreamStages = upstreamStages.concat(getAllUpstreamDependencies(pipeline, stageToTest));
          }
        });
      }
      return _.uniq(upstreamStages);
    }

    function enableParallelExecution(pipeline) {
      pipeline.stages.forEach((stage, index) => {
        stage.refId = index + '';
        if (index > 0) {
          stage.requisiteStageRefIds = [(index - 1) + ''];
        } else {
          stage.requisiteStageRefIds = [];
        }
      });
      pipeline.parallel = true;
    }

    return {
      getPipelinesForApplication: getPipelinesForApplication,
      getStrategiesForApplication: getStrategiesForApplication,
      savePipeline: savePipeline,
      deletePipeline: deletePipeline,
      renamePipeline: renamePipeline,
      triggerPipeline: triggerPipeline,
      buildViewStateCacheKey: buildViewStateCacheKey,
      getDependencyCandidateStages: getDependencyCandidateStages,
      getAllUpstreamDependencies: getAllUpstreamDependencies,
      enableParallelExecution: enableParallelExecution,
    };
  });
