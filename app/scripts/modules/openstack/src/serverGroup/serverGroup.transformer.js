'use strict';

import _ from 'lodash';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.openstack.serverGroup.transformer', [])
  .factory('openstackServerGroupTransformer', ['$q', function($q) {
    function normalizeServerGroup(serverGroup) {
      if (serverGroup.loadBalancers) {
        serverGroup.loadBalancerIds = _.map(serverGroup.loadBalancers, function(lb) {
          return /^openstack:/.test(lb) ? lb.split(':')[4] : lb;
        });
        serverGroup.loadBalancers = _.map(serverGroup.loadBalancers, function(lb) {
          return /^openstack:/.test(lb) ? lb.split(':')[5] : lb;
        });
      }

      return $q.when(serverGroup); // no-op
    }

    function convertServerGroupCommandToDeployConfiguration(base) {
      //avoid copying the backingData or viewState, which are expensive to copy over
      var params = _.omit(
        base,
        'backingData',
        'viewState',
        'selectedProvider',
        'credentials',
        'stack',
        'region',
        'account',
        'cloudProvider',
        'application',
        'type',
        'freeFormDetails',
        'userDataType',
        'userData',
      );
      var command = {
        type: base.type,
        application: base.application,
        cloudProvider: 'openstack',
        account: base.account || base.credentials,
        region: base.region,
        stack: base.stack,
        freeFormDetails: base.freeFormDetails,
        userDataType: base.userDataType,
        userData: base.userData,
        serverGroupParameters: params,
        strategy: base.strategy,
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
  }]);
