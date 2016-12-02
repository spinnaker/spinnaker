'use strict';

import {API_SERVICE} from 'core/api/api.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.serverGroup.configure.common.service', [
  API_SERVICE,
  require('../../../cloudProvider/serviceDelegate.service.js'),
  require('core/config/settings.js')
])
  .factory('serverGroupCommandBuilder', function (settings, API, serviceDelegate) {

    function getServerGroup(application, account, region, serverGroupName) {
      return API.one('applications').one(application).all('serverGroups').all(account).all(region).one(serverGroupName).call();
    }

    function getDelegate(provider) {
      return serviceDelegate.getDelegate(provider, 'serverGroup.commandBuilder');
    }

    function buildNewServerGroupCommand(application, provider, options) {
      return getDelegate(provider).buildNewServerGroupCommand(application, options);
    }

    function buildServerGroupCommandFromExisting(application, serverGroup, mode) {
      return getDelegate(serverGroup.type).buildServerGroupCommandFromExisting(application, serverGroup, mode);
    }

    function buildNewServerGroupCommandForPipeline(provider, currentStage, pipeline) {
      return getDelegate(provider).buildNewServerGroupCommandForPipeline(currentStage, pipeline);
    }

    function buildServerGroupCommandFromPipeline(application, cluster, currentStage, pipeline) {
      return getDelegate(cluster.provider).buildServerGroupCommandFromPipeline(application, cluster, currentStage, pipeline);
    }

    return {
      getServerGroup: getServerGroup,
      buildNewServerGroupCommand: buildNewServerGroupCommand,
      buildServerGroupCommandFromExisting: buildServerGroupCommandFromExisting,
      buildNewServerGroupCommandForPipeline: buildNewServerGroupCommandForPipeline,
      buildServerGroupCommandFromPipeline: buildServerGroupCommandFromPipeline,
    };
});

