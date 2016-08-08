'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.openstack.serverGroup.transformer', [
    require('../../core/utils/lodash.js'),
  ])
  .factory('openstackServerGroupTransformer', function ($q, _) {

    function normalizeServerGroup(serverGroup) {
      return $q.when(serverGroup); // no-op
    }

    function convertServerGroupCommandToDeployConfiguration(base) {
      //avoid copying the backingData or viewState, which are expensive to copy over
      var params = _.omit(base, 'backingData', 'viewState', 'selectedProvider', 'credentials', 'loadBalancers',
        'stack', 'region', 'account', 'cloudProvider', 'application', 'type', 'freeFormDetails');
      var command = {
        type: base.type,
        application: base.application,
        cloudProvider: 'openstack',
        account: base.account || base.credentials,
        region: base.region,
        stack: base.stack,
        freeFormDetails: base.freeFormDetails,
        serverGroupParameters: params
      };

      if (base.viewState.mode === 'clone' && base.source) {
        command.source = base.source;
      }
      return command;
    }

    return {
      convertServerGroupCommandToDeployConfiguration: convertServerGroupCommandToDeployConfiguration,
      normalizeServerGroup: normalizeServerGroup,
    };
  });
