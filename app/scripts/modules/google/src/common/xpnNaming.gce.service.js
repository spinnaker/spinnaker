'use strict';

import _ from 'lodash';

const angular = require('angular');

module.exports = angular.module('spinnaker.gce.common.xpnNaming.service', [])
  .factory('gceXpnNamingService', function () {

    let deriveProjectId = (resourceWithSelfLink) => {
      let pathSegments = resourceWithSelfLink.selfLink.split('/');
      return pathSegments[pathSegments.indexOf('projects') + 1];
    };

    let decorateXpnResourceIfNecessary = (projectId, xpnResource) => {
      if (!xpnResource) {
        return null;
      }

      // A network selfLink looks like: https://www.googleapis.com/compute/beta/projects/test-host-project/global/networks/default
      // A subnet selfLink looks like: https://www.googleapis.com/compute/beta/projects/my-test-service-project/regions/us-west1/subnetworks/default
      let xpnResourcePathSegments = xpnResource.split('/');
      let xpnResourceProjectId = xpnResourcePathSegments[xpnResourcePathSegments.indexOf('projects') + 1];
      let xpnResourceName = _.last(xpnResourcePathSegments);
      if (xpnResourceProjectId !== projectId) {
        return xpnResourceProjectId + '/' + xpnResourceName;
      } else {
        return xpnResourceName;
      }
    };

    return {
      deriveProjectId,
      decorateXpnResourceIfNecessary,
    };
  });
