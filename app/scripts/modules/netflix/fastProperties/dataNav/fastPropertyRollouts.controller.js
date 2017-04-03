'use strict';

import './fpRollout.less';

import { APPLICATION_READ_SERVICE } from 'core/application/service/application.read.service';
import { FAST_PROPERTY_READ_SERVICE } from '../fastProperty.read.service';
import { EXECUTION_GROUP_COMPONENT } from 'core/delivery/executionGroup/executionGroup.component';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.fastProperties.rollouts.controller', [
    FAST_PROPERTY_READ_SERVICE,
    require('./../fastProperty.write.service.js'),
    EXECUTION_GROUP_COMPONENT,
    APPLICATION_READ_SERVICE
  ])
  .controller('FastPropertyRolloutController', function ($scope, $log, fastPropertyReader, fastPropertyWriter,
                                                          applicationReader) {
    var vm = this;

    vm.application = undefined;

    vm.fetchApplication = () => {
      applicationReader.getApplication('spinnakerfp')
        .then((application) => {
          vm.application = application;
          return vm.application;
        })
        .then((application) => {
          application.executions.activate();
          return application;
        })
        .then((application) => {
          application.executions.onRefresh($scope, normalizeExecutionNames, dataInitializationFailure);

        });
    };

    vm.fetchApplication();

    let dataInitializationFailure = () => {
      this.viewState.loading = false;
      this.viewState.initializationError = true;
    };

    function normalizeExecutionNames() {
      if (vm.application.executions.loadFailure) {
        dataInitializationFailure();
      }
      let executions = vm.application.executions.data || [];
      var configurations = vm.application.pipelineConfigs.data || [];
      executions.forEach(function(execution) {
        if (execution.pipelineConfigId) {
          var configMatches = configurations.filter(function(configuration) {
            return configuration.id === execution.pipelineConfigId;
          });
          if (configMatches.length) {
            execution.name = configMatches[0].name;
          }
        }
      });
      $scope.application = vm.application;
    }
    return vm;
  });
