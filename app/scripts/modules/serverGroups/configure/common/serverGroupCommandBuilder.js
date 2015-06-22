'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.common.service', [
  require('restangular'),
  require('../caches/deckCacheFactory.js'),
  require('../../../delegation/serviceDelegate.service.js')
])
  .factory('serverGroupCommandBuilder', function (settings, Restangular, serviceDelegate) {

    function getServerGroup(application, account, region, serverGroupName) {
      return Restangular.one('applications', application).all('serverGroups').all(account).all(region).one(serverGroupName).get();
    }

    function getDelegate(provider) {
      return serviceDelegate.getDelegate(provider, 'ServerGroupCommandBuilder');
    }

    function buildNewServerGroupCommand(application, provider, options) {
      return getDelegate(provider).buildNewServerGroupCommand(application, options);
    }

    function buildServerGroupCommandFromExisting(application, serverGroup, mode) {
      return getDelegate(serverGroup.type).buildServerGroupCommandFromExisting(application, serverGroup, mode);
    }

    function buildNewServerGroupCommandForPipeline(provider) {
      return getDelegate(provider).buildNewServerGroupCommandForPipeline();
    }

    function buildServerGroupCommandFromPipeline(application, cluster) {
      return getDelegate(cluster.provider).buildServerGroupCommandFromPipeline(application, cluster);
    }

    return {
      getServerGroup: getServerGroup,
      buildNewServerGroupCommand: buildNewServerGroupCommand,
      buildServerGroupCommandFromExisting: buildServerGroupCommandFromExisting,
      buildNewServerGroupCommandForPipeline: buildNewServerGroupCommandForPipeline,
      buildServerGroupCommandFromPipeline: buildServerGroupCommandFromPipeline,
    };
});

