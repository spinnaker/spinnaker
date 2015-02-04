'use strict';


angular.module('deckApp.pipelines.config.service', [
  'restangular',
  'deckApp.settings',
  'deckApp.authentication.service',
])
  .factory('pipelineConfigService', function (settings, Restangular, authenticationService) {

    function getPipelinesForApplication(applicationName) {
      return Restangular.one('applications', applicationName).all('pipelineConfigs').getList();
    }

    function deletePipeline(applicationName, pipelineName) {
      return Restangular.all('pipelines').one(applicationName, pipelineName).remove();
    }

    function savePipeline(pipeline) {
      pipeline.stages.forEach(function(stage) {
        delete stage.isNew;
        if (!stage.name) {
          delete stage.name;
        }
      });
      return Restangular.all('pipelines').post(pipeline);
    }

    function renamePipeline(applicationName, currentName, newName) {
      return Restangular.all('pipelines').all('move').post({
        application: applicationName,
        from: currentName,
        to: newName
      });
    }

    function triggerPipeline(applicationName, pipelineName) {
      return Restangular.one('applications', applicationName).all('pipelineConfigs')
        .customPOST(
          {}, pipelineName, { user: authenticationService.getAuthenticatedUser().name }
      );
    }

    return {
      getPipelinesForApplication: getPipelinesForApplication,
      savePipeline: savePipeline,
      deletePipeline: deletePipeline,
      renamePipeline: renamePipeline,
      triggerPipeline: triggerPipeline,
    };

  });
