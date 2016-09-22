'use strict';

import _ from 'lodash';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.actions.editJson', [])
  .controller('EditPipelineJsonModalCtrl', function($scope, pipeline, $uibModalInstance) {

    this.cancel = $uibModalInstance.dismiss;

    function removeImmutableFields(obj) {
      delete obj.name;
      delete obj.application;
      delete obj.index;
      delete obj.id;
    }

    this.initialize = function() {
      var pipelineCopy = _.cloneDeepWith(pipeline, function (value) {
        if (value && value.$$hashKey) {
          delete value.$$hashKey;
        }
      });
      removeImmutableFields(pipelineCopy);

      $scope.isStrategy = pipelineCopy.strategy || false;

      $scope.command = {
        pipelineJSON: JSON.stringify(pipelineCopy, null, 2)
      };
    };

    this.updatePipeline = function() {
      try {
        var parsed = JSON.parse($scope.command.pipelineJSON);
        parsed.appConfig = parsed.appConfig || {};

        removeImmutableFields(parsed);
        angular.extend(pipeline, parsed);
        $uibModalInstance.close();
      } catch (e) {
        $scope.command.invalid = true;
        $scope.command.errorMessage = e.message;
      }
    };

    this.initialize();
  });

