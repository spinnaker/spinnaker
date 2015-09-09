'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.securityGroup.write.service', [
    require('../caches/infrastructureCaches.js'),
    require('../tasks/taskExecutor.js')
  ])
  .factory('securityGroupWriter', function (taskExecutor, infrastructureCaches) {

    function upsertSecurityGroup(command, application, descriptor) {
      command.type = 'upsertSecurityGroup';
      command.credentials = command.credentials || command.accountName;

      var operation = taskExecutor.executeTask({
        job: [
          command
        ],
        application: application,
        description: descriptor + ' Security Group: ' + command.name
      });

      infrastructureCaches.clearCache('securityGroups');

      return operation;
    }

    function deleteSecurityGroup(securityGroup, application, params={}) {
      params.type = 'deleteSecurityGroup';
      params.securityGroupName = securityGroup.name;
      params.regions = [securityGroup.region];
      params.credentials = securityGroup.accountId;
      params.providerType = securityGroup.providerType;

      var operation = taskExecutor.executeTask({
        job: [params],
        application: application,
        description: 'Delete security group: ' + securityGroup.name + ' in ' + securityGroup.accountId + ':' + securityGroup.region
      });

      infrastructureCaches.clearCache('securityGroups');

      return operation;
    }


    return {
      upsertSecurityGroup: upsertSecurityGroup,
      deleteSecurityGroup: deleteSecurityGroup,
    };

  }).name;
