'use strict';


angular.module('deckApp.serverGroup.configure.common.service', [
  'restangular',
  'deckApp.settings',
  'deckApp.delegation'
])
  .factory('serverGroupCommandBuilder', function (settings, Restangular, serviceDelegate) {

    function getServerGroup(application, account, region, serverGroupName) {
      return Restangular.one('applications', application).all('serverGroups').all(account).all(region).one(serverGroupName).get();
    }

    function getDelegate(provider) {
      return serviceDelegate.getDelegate(provider, 'ServerGroupCommandBuilder');
    }

    function buildNewServerGroupCommand(application, provider) {
      return getDelegate(provider).buildNewServerGroupCommand(application);
    }

    function buildServerGroupCommandFromExisting(application, serverGroup, mode) {
      return getDelegate(serverGroup.type).buildServerGroupCommandFromExisting(application, serverGroup, mode);
    }

    return {
      getServerGroup: getServerGroup,
      buildNewServerGroupCommand: buildNewServerGroupCommand,
      buildServerGroupCommandFromExisting: buildServerGroupCommandFromExisting
    };
});

