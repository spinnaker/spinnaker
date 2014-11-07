'use strict';

angular.module('deckApp')
  .factory('pipelines', function($stateParams, scheduler, orchestratedItem, $http, $timeout, settings, $q, RxService, applicationLevelScheduledCache, appendTransform) {
    function getCurrentPipeline() {
      var deferred = $q.defer();
      getPipelines().then(function(pipelines) {
        // pipelines/:pipeline endpoint doesn't appear to work ATM
        deferred.resolve(pipelines.filter(function(execution) {
          return execution.id === $stateParams.executionId;
        })[0]);
      });
      return deferred.promise;
    }

    function getCurrentStage() {
      var deferred = $q.defer();
      getCurrentPipeline().then(function(pipeline) {
        deferred.resolve(pipeline.stages.reduce(function(acc, stage) {
          if (stage.name === $stateParams.stageName) {
            acc = stage;
          }
          return acc;
        }, {}));
      }, function() {
      });
      return deferred.promise;
    }

    function getPipelines() {
      var deferred = $q.defer();
      $http({
        method: 'GET',
        cache: applicationLevelScheduledCache,
        transformResponse: appendTransform(function(pipelines) {
          pipelines.forEach(function(pipeline) {
            orchestratedItem.defineProperties(pipeline);
            pipeline.stages.forEach(orchestratedItem.defineProperties);
            Object.defineProperty(pipeline, 'currentStage', {
              get: function() {
                if (pipeline.isCompleted) {
                  return pipeline.stages.indexOf(
                    pipeline.stages[pipeline.stages.length -1]
                  );
                }
                if (pipeline.isFailed) {
                  return pipeline.stages.indexOf(
                    pipeline.stages.filter(function(stage) {
                      return stage.isFailed;
                    })[0]
                  );
                }
                if (pipeline.isRunning) {
                  return pipeline.stages.indexOf(
                    pipeline.stages.filter(function(stage) {
                      return stage.isRunning;
                    })[0]
                  );
                }
              },
            });
          });
          return pipelines;
        }),
        url: [
          settings.gateUrl,
          'applications',
          $stateParams.application,
          'pipelines',
        ].join('/'),
      }).then(function(resp) {
        deferred.resolve(resp.data);
      });
      return deferred.promise;
    }

    return {
      getAll: getPipelines,
      subscribeAll: function(fn) {
        return scheduler
          .get()
          .flatMap(function() {
            return RxService.Observable.fromPromise(getPipelines());
          })
          .subscribe(fn);
      },
      getCurrentPipeline: getCurrentPipeline,
      subscribeToCurrentPipeline: function(fn) {
        return scheduler
          .get()
          .flatMap(function() {
            return RxService.Observable.fromPromise(getCurrentPipeline());
          })
          .subscribe(fn);
      },
      getCurrentStage: getCurrentStage,
      subscribeToCurrentStage: function(fn) {
        return scheduler
          .get()
          .flatMap(function() {
            return RxService.Observable.fromPromise(getCurrentStage());
          })
          .subscribe(fn);
      },
    };
  });
