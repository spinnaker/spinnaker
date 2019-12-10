import { IPromise } from 'angular';

import { API } from 'core/api';
import { IManagedApplicationSummary } from 'core/domain';

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
      .get();
  }

  public static getApplicationVetos(): IPromise<string[]> {
    return API.one('managed')
      .all('vetos')
      .one('ApplicationVeto')
      .all('rejections')
      .get();
  }
}
