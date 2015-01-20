'use strict';

angular
  .module('deckApp.securityGroup.write.service', [
    'deckApp.caches.infrastructure',
    'deckApp.taskExecutor.service'
  ])
  .factory('securityGroupWriter' ,function (taskExecutor, infrastructureCaches) {

    function upsertSecurityGroup(command, application, descriptor) {
      command.type = 'upsertSecurityGroup';

      var operation = taskExecutor.executeTask({
        job: [
          command
        ],
        application: application,
        description: descriptor + ' Security Group: ' + command.name
      });

      operation.then(infrastructureCaches.securityGroups.removeAll);

      return operation;
    }

    function deleteSecurityGroup(securityGroup, application) {
      var operation = taskExecutor.executeTask({
        job: [
          {
            type: 'deleteSecurityGroup',
            securityGroupName: securityGroup.name,
            regions: [securityGroup.region],
            credentials: securityGroup.accountId,
            providerType: securityGroup.providerType,
            vpcId: securityGroup.vpcId
          }
        ],
        application: application,
        description: 'Delete security group: ' + securityGroup.name + ' in ' + securityGroup.accountId + ':' + securityGroup.region
      });

      operation.then(infrastructureCaches.securityGroups.removeAll);

      return operation;
    }


    return {
      upsertSecurityGroup: upsertSecurityGroup,
      deleteSecurityGroup: deleteSecurityGroup,
    };

  });
