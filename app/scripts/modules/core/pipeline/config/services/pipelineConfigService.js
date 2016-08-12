'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.services.configService', [
  require('../../../api/api.service'),
  require('../../../utils/lodash.js'),
  require('../../../authentication/authentication.service.js'),
  require('../../../cache/viewStateCache.js'),
])
  .factory('pipelineConfigService', function (_, $q, API, authenticationService, viewStateCache) {

    var configViewStateCache = viewStateCache.createCache('pipelineConfig', { version: 1 });

    function buildViewStateCacheKey(applicationName, pipelineName) {
      return [applicationName, pipelineName].join(':');
    }

    function getPipelinesForApplication(applicationName) {
      return API.one('applications').one(applicationName).all('pipelineConfigs').getList().then(function (pipelines) {
        pipelines.forEach(p => p.stages = p.stages || []);
        return sortPipelines(pipelines);
      });
    }

    function getStrategiesForApplication(applicationName) {
      return API.one('applications').one(applicationName).all('strategyConfigs').getList().then(function (pipelines) {
        pipelines.forEach(p => p.stages = p.stages || []);
        return sortPipelines(pipelines);
      });
    }

    function getHistory(id, count = 20) {
      return API.one('pipelineConfigs', id).all('history').withParams({count: count}).getList();
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
      return API.one(pipeline.strategy ? 'strategies' : 'pipelines').one(applicationName, pipelineName).remove();
    }

    function savePipeline(pipeline) {
      delete pipeline.isNew;
      pipeline.stages.forEach(function(stage) {
        delete stage.isNew;
        if (!stage.name) {
          delete stage.name;
        }
      });
      return API.one( pipeline.strategy ? 'strategies' : 'pipelines').data(pipeline).post();
    }

    function renamePipeline(applicationName, pipeline, currentName, newName) {
      configViewStateCache.remove(buildViewStateCacheKey(applicationName, currentName));
      return API.one(pipeline.strategy ? 'strategies' : 'pipelines').all('move').data({
        application: applicationName,
        from: currentName,
        to: newName
      }).post();
    }

    function triggerPipeline(applicationName, pipelineName, body) {
      body = body || {};
      body.user = authenticationService.getAuthenticatedUser().name;
      return API.one('pipelines').one(applicationName).one(pipelineName).data(body).post();
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
      getPipelinesForApplication,
      getStrategiesForApplication,
      getHistory,
      savePipeline,
      deletePipeline,
      renamePipeline,
      triggerPipeline,
      buildViewStateCacheKey,
      getDependencyCandidateStages,
      getAllUpstreamDependencies,
      enableParallelExecution,
    };
  });
