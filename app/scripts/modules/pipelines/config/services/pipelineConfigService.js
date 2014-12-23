'use strict';


angular.module('deckApp.pipelines')
  .factory('pipelineConfigService', function (settings, Restangular, authenticationService) {

    var gateEndpoint = Restangular.withConfig(function (RestangularConfigurer) {
      RestangularConfigurer.setBaseUrl(settings.gateUrl);
    });

    function getPipelinesForApplication(applicationName) {
      return gateEndpoint.one('applications', applicationName).all('pipelineConfigs').getList();
    }

    function deletePipeline(applicationName, pipelineName) {
      return gateEndpoint.all('pipelines').one(applicationName, pipelineName).remove();
    }

    function savePipeline(pipeline) {
      pipeline.stages.forEach(function(stage) {
        delete stage.isNew;
        if (!stage.name) {
          delete stage.name;
        }
      });
      return gateEndpoint.all('pipelines').post(pipeline);
    }

    function renamePipeline(applicationName, currentName, newName) {
      return gateEndpoint.all('pipelines').all('move').post({
        application: applicationName,
        from: currentName,
        to: newName
      });
    }

    function triggerPipeline(applicationName, pipelineName) {
      return gateEndpoint.one('applications', applicationName).all('pipelineConfigs')
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
