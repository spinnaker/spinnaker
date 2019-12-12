import { IPromise } from 'angular';

import { API } from 'core/api';
import { IManagedApplicationSummary, ManagedResourceStatus } from 'core/domain';

export const getResourceKindForLoadBalancerType = (type: string) => {
  switch (type) {
    case 'classic':
      return 'classic-load-balancer';
    case 'application':
      return 'application-load-balancer';
    default:
      return null;
  }
};

export class ManagedReader {
  public static getApplicationSummary(app: string): IPromise<IManagedApplicationSummary> {
    return API.one('managed')
      .one('application', app)
      .withParams({ includeDetails: true })
      .get()
      .then((response: IManagedApplicationSummary) => {
        // Individual resources don't update their status when an application is paused/resumed,
        // so for now let's swap to a PAUSED status and keep things simpler in downstream components.
        if (response.applicationPaused) {
          response.resources.forEach(resource => (resource.status = ManagedResourceStatus.PAUSED));
        }

        response.resources.forEach(resource => (resource.isPaused = resource.status === ManagedResourceStatus.PAUSED));

        return response;
      });
  }
}
