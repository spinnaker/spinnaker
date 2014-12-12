'use strict';


angular.module('deckApp.pipelines')
  .factory('pipelineConfigService', function (settings, Restangular) {

    var mayoEndpoint = Restangular.withConfig(function (RestangularConfigurer) {
      RestangularConfigurer.setBaseUrl(settings.mayoUrl);
    });

    var gateEndpoint = Restangular.withConfig(function (RestangularConfigurer) {
      RestangularConfigurer.setBaseUrl(settings.gateUrl);
    });

    function getPipelinesForApplication(applicationName) {
      return gateEndpoint.one('applications', applicationName).all('pipelineConfigs').getList();
    }

    function deletePipeline(applicationName, pipelineName) {
      return mayoEndpoint.all('pipelines').one(applicationName, pipelineName).remove();
    }

    function savePipeline(pipeline) {
      pipeline.stages.forEach(function(stage) {
        delete stage.isNew;
      });
      return mayoEndpoint.all('pipelines').post(pipeline);
    }

    function renamePipeline(applicationName, currentName, newName) {
      return mayoEndpoint.all('pipelines').all('move').post({
        application: applicationName,
        from: currentName,
        to: newName
      });
    }

    return {
      getPipelinesForApplication: getPipelinesForApplication,
      savePipeline: savePipeline,
      deletePipeline: deletePipeline,
      renamePipeline: renamePipeline
    };

  });
