'use strict';

import { module } from 'angular';
import _ from 'lodash';

export const GOOGLE_COMMON_XPNNAMING_GCE_SERVICE = 'spinnaker.gce.common.xpnNaming.service';
export const name = GOOGLE_COMMON_XPNNAMING_GCE_SERVICE; // for backwards compatibility
module(GOOGLE_COMMON_XPNNAMING_GCE_SERVICE, []).factory('gceXpnNamingService', function () {
  const deriveProjectId = (resourceWithSelfLink) => {
    const pathSegments = resourceWithSelfLink.selfLink.split('/');
    return pathSegments[pathSegments.indexOf('projects') + 1];
  };

  const decorateXpnResourceIfNecessary = (projectId, xpnResource) => {
    if (!xpnResource) {
      return null;
    }

    // A network selfLink looks like: https://compute.googleapis.com/compute/beta/projects/test-host-project/global/networks/default
    // A subnet selfLink looks like: https://compute.googleapis.com/compute/beta/projects/my-test-service-project/regions/us-west1/subnetworks/default
    const xpnResourcePathSegments = xpnResource.split('/');
    const xpnResourceProjectId = xpnResourcePathSegments[xpnResourcePathSegments.indexOf('projects') + 1];
    const xpnResourceName = _.last(xpnResourcePathSegments);
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
