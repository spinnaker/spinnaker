'use strict';

const angular = require('angular');

import {
  CLOUD_PROVIDER_REGISTRY,
  INSTANCE_READ_SERVICE,
  INSTANCE_WRITE_SERVICE
} from '@spinnaker/core';

module.exports = angular.module('spinnaker.oraclebmcs.instance.details.controller', [
  require('angular-ui-router').default,
  INSTANCE_WRITE_SERVICE,
  INSTANCE_READ_SERVICE,
  CLOUD_PROVIDER_REGISTRY
])
  .controller('oraclebmcsInstanceDetailsCtrl', function ($scope, $q, instanceWriter, instanceReader, cloudProviderRegistry, app, instance) {

    $scope.application = app;

    let initialize = app.isStandalone ?
      retrieveInstance() :
      $q.all([app.serverGroups.ready()]).then(retrieveInstance);

    initialize.then(() => {
      if (!$scope.$$destroyed && !app.isStandalone) {
        app.serverGroups.onRefresh($scope, retrieveInstance);
      }
    });

    function retrieveInstance() {
      let instanceSummary, account, region;
      if (!$scope.application.serverGroups) {
        instanceSummary = {};
        account = instance.account;
        region = instance.region;
      } else {
        $scope.application.serverGroups.data.some((serverGroup) => {
          return serverGroup.instances.some((possibleInstance) => {
            if (possibleInstance.id === instance.instanceId || possibleInstance.name === instance.instanceId) {
              instanceSummary = possibleInstance;
              account = serverGroup.account;
              region = serverGroup.region;
              return true;
            }
          });
        });
      }

      $scope.instance = instanceSummary;
      instanceReader.getInstanceDetails(account, region, instance.instanceId).then((instanceDetails) => {
        Object.assign($scope.instance, instanceDetails);
      });
    }
  });
