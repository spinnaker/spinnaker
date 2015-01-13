'use strict';

angular
  .module('deckApp.securityGroup.write.service', ['deckApp.caches.infrastructure'])
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


    return {
      upsertSecurityGroup: upsertSecurityGroup
    };

  });
