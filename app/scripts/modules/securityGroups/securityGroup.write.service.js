'use strict';

angular
  .module('deckApp.securityGroup.write.service', ['deckApp.caches.infrastructure'])
  .factory('securityGroupWriter' ,function (taskExecutor, infrastructureCaches) {

    function upsertSecurityGroup(securityGroup, applicationName, descriptor) {
      securityGroup.type = 'upsertSecurityGroup';
      infrastructureCaches.securityGroups.removeAll();
      return taskExecutor.executeTask({
        job: [
          securityGroup
        ],
        application: applicationName,
        description: descriptor + ' Security Group: ' + securityGroup.name
      });
    }


    return {
      upsertSecurityGroup: upsertSecurityGroup
    };

  });