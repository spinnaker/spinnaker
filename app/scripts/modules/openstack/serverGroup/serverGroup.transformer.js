'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.openstack.serverGroup.transformer', [
    require('../../core/utils/lodash.js'),
  ])
  .factory('openstackServerGroupTransformer', function ($q, _) {

    function normalizeServerGroup(serverGroup) {
      if( serverGroup.loadBalancers ) {
        serverGroup.loadBalancers = _.map(serverGroup.loadBalancers, function(lb) {
          return /^openstack:/.test(lb) ? lb.split(':')[5] : lb;
        });
      }

      //TODO(jwest): remove this once the back-end supplies properly formatted UUIDs
      if( serverGroup.launchConfig && serverGroup.launchConfig.securityGroups ) {
        serverGroup.launchConfig.securityGroups = _.map(serverGroup.launchConfig.securityGroups, function(sg) {
          return /^\[u\'/.test(sg) ? sg.split('\'')[1] : sg;
        });
      }

      return $q.when(serverGroup); // no-op
    }

    function convertServerGroupCommandToDeployConfiguration(base) {
      //avoid copying the backingData or viewState, which are expensive to copy over
      var params = _.omit(base, 'backingData', 'viewState', 'selectedProvider', 'credentials',
        'stack', 'region', 'account', 'cloudProvider', 'application', 'type', 'freeFormDetails', 'userData');
      var command = {
        type: base.type,
        application: base.application,
        cloudProvider: 'openstack',
        account: base.account || base.credentials,
        region: base.region,
        stack: base.stack,
        freeFormDetails: base.freeFormDetails,
        userData: base.userData,
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
