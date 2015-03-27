'use strict';


angular.module('deckApp.pipelines.config.service', [
  'restangular',
  'deckApp.settings',
  'deckApp.authentication.service',
  'deckApp.caches.viewStateCache'
])
  .factory('pipelineConfigService', function (settings, Restangular, authenticationService, viewStateCache) {

    function buildViewStateCacheKey(pipelineName) {
      return ['pipelineConfig', pipelineName].join(':');
    }

    function getPipelinesForApplication(applicationName) {
      return Restangular.one('applications', applicationName).all('pipelineConfigs').getList();
    }

    function deletePipeline(applicationName, pipelineName) {
      return Restangular.all('pipelines').one(applicationName, pipelineName).remove();
    }

    function savePipeline(pipeline, retainIsNewFlags) {
      pipeline.stages.forEach(function(stage) {
        if (!retainIsNewFlags) {
          delete stage.isNew;
        }
        if (!stage.name) {
          delete stage.name;
        }
      });
      return Restangular.all('pipelines').post(pipeline);
    }

    function renamePipeline(applicationName, currentName, newName) {
      viewStateCache.clearViewState(applicationName, buildViewStateCacheKey(currentName));
      return Restangular.all('pipelines').all('move').post({
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

    return {
      getPipelinesForApplication: getPipelinesForApplication,
      savePipeline: savePipeline,
      deletePipeline: deletePipeline,
      renamePipeline: renamePipeline,
      triggerPipeline: triggerPipeline,
      buildViewStateCacheKey: buildViewStateCacheKey,
    };

  });
