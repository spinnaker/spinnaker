'use strict';

import { module } from 'angular';

import { InstanceReader, INSTANCE_WRITE_SERVICE } from '@spinnaker/core';
import UIROUTER_ANGULARJS from '@uirouter/angularjs';

export const ORACLE_INSTANCE_DETAILS_INSTANCE_DETAILS_CONTROLLER = 'spinnaker.oracle.instance.details.controller';
export const name = ORACLE_INSTANCE_DETAILS_INSTANCE_DETAILS_CONTROLLER; // for backwards compatibility
module(ORACLE_INSTANCE_DETAILS_INSTANCE_DETAILS_CONTROLLER, [UIROUTER_ANGULARJS, INSTANCE_WRITE_SERVICE]).controller(
  'oracleInstanceDetailsCtrl',
  [
    '$scope',
    '$q',
    'instanceWriter',
    'app',
    'instance',
    function ($scope, $q, instanceWriter, app, instance) {
      $scope.application = app;

      const initialize = app.isStandalone
        ? retrieveInstance()
        : $q.all([app.serverGroups.ready()]).then(retrieveInstance);

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
        InstanceReader.getInstanceDetails(account, region, instance.instanceId).then((instanceDetails) => {
          Object.assign($scope.instance, instanceDetails);
        });
      }
    },
  ],
);
