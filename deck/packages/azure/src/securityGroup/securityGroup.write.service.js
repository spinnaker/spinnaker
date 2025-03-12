'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import { module } from 'angular';
import _ from 'lodash';

import { FirewallLabels, InfrastructureCaches, TaskExecutor } from '@spinnaker/core';

export const AZURE_SECURITYGROUP_SECURITYGROUP_WRITE_SERVICE = 'spinnaker.azure.securityGroup.write.service';
export const name = AZURE_SECURITYGROUP_SECURITYGROUP_WRITE_SERVICE; // for backwards compatibility
module(AZURE_SECURITYGROUP_SECURITYGROUP_WRITE_SERVICE, [UIROUTER_ANGULARJS]).factory(
  'azureSecurityGroupWriter',
  function () {
    function upsertSecurityGroup(securityGroup, application, descriptor, params = {}) {
      params.securityGroupName = securityGroup.name;

      // We want to extend params with all attributes from securityGroup, but only if they don't already exist.
      _.assignWith(params, securityGroup, function (value, other) {
        return _.isUndefined(value) ? other : value;
      });

      const operation = TaskExecutor.executeTask({
        job: [params],
        application: application,
        description: `${descriptor} ${FirewallLabels.get('Firewall')}: ${name}`,
      });

      InfrastructureCaches.clearCache('securityGroup');

      return operation;
    }

    function deleteSecurityGroup(securityGroup, application, params = {}) {
      params.type = 'deleteSecurityGroup';
      params.securityGroupName = securityGroup.name;
      params.regions = [securityGroup.region];
      params.credentials = securityGroup.accountId;
      //params.cloudProvider = securityGroup.providerType;
      params.appName = application.name;

      const operation = TaskExecutor.executeTask({
        job: [params],
        application: application,
        description: `Delete ${FirewallLabels.get('Firewalls')}: ${securityGroup.name}`,
      });

      InfrastructureCaches.clearCache('securityGroup');

      return operation;
    }

    return {
      deleteSecurityGroup: deleteSecurityGroup,
      upsertSecurityGroup: upsertSecurityGroup,
    };
  },
);
