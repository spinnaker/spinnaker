'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.services.configService', [
  require('exports?"restangular"!imports?_=lodash!restangular'),
  require('../../../utils/lodash.js'),
  require('../../../authentication/authentication.service.js'),
  require('../../../cache/viewStateCache.js'),
  require('../../../confirmationModal/confirmationModal.service.js'),
])
  .factory('pipelineConfigService', function (_, $q, settings, Restangular,
                                              authenticationService, viewStateCache,
                                              $uibModal) {

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
      if (stage.requisiteStageRefIds  && stage.requisiteStageRefIds.length) {
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
      pipeline.stageCounter = 0;
      pipeline.stages.forEach(function(stage) {
        pipeline.stageCounter++;
        stage.refId = pipeline.stageCounter + '';
        if (pipeline.stageCounter > 1) {
          stage.requisiteStageRefIds = [(pipeline.stageCounter - 1) + ''];
        } else {
          stage.requisiteStageRefIds = [];
        }
      });
      pipeline.parallel = true;
      pipeline.stageCounter = pipeline.stages.length;
    }

    function disableParallelExecution(pipeline) {
      delete pipeline.stageCounter;
      pipeline.stages.forEach(function(stage) {
        delete stage.refId;
        delete stage.requisiteStageRefIds;
      });
      delete pipeline.parallel;
    }

    function toggleTrigger(pipeline, index) {
      var trigger = pipeline.triggers[index];
      return $uibModal.open({
        templateUrl: require('../../../delivery/triggers/toggleTrigger.modal.html'),
        controller: 'ToggleTriggerModalCtrl',
        controllerAs: 'toggleTriggerCtrl',
        resolve: {
          pipeline: function() { return pipeline; },
          trigger: function() { return trigger; }
        }
      }).result;
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
      disableParallelExecution: disableParallelExecution,
      toggleTrigger: toggleTrigger,
    };

  });
