'use strict';

import _ from 'lodash';

let angular = require('angular');

module.exports = angular.module('spinnaker.netflix.pipeline.stage.acaTask.transformer', [
  require('core/orchestratedItem/orchestratedItem.transformer.js'),
])
  .service('acaTaskTransformer', function($log, orchestratedItemTransformer) {

    function getException (stage) {
      orchestratedItemTransformer.defineProperties(stage);
      return stage && stage.isFailed ? stage.failureMessage : null;
    }


    this.transform = function(application, execution) {
      execution.stages.forEach(function(stage) {
        if (stage.type === 'acaTask') {
          orchestratedItemTransformer.defineProperties(stage);
          stage.exceptions = [];


          if (getException(stage)) {
            stage.exceptions.push('Canary failure: ' + getException(stage));
          }

          stage.exceptions = _.uniq(stage.exceptions);

          var status = stage.status;

          var canaryStatus = stage.context.canary.status;

          var canaryResult = stage.context.canary.canaryResult && stage.context.canary.canaryResult.overallResult;

          if (canaryStatus && status !== 'CANCELED') {
            if (canaryStatus.status === 'LAUNCHED' || canaryStatus.status === 'RUNNING') {
              status = 'RUNNING';
            }
            if (canaryStatus.complete && canaryResult === 'SUCCESS') {
              status = 'SUCCEEDED';
            }
            if (canaryStatus.status === 'DISABLED') {
              status = 'DISABLED';
            }
            if (canaryStatus.status === 'FAILED' || canaryResult === 'FAILURE') {
              status = 'FAILED';
            }
            if (canaryStatus.status === 'TERMINATED') {
              status = 'TERMINATED';
            }
            canaryStatus.status = status;
          } else {
            stage.context.canary.status = { status: 'UNKNOWN' };
          }
          stage.status = status;

        }
      });
    };
  });
