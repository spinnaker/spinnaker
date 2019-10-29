import { IPromise } from 'angular';

import { API } from 'core/api';
import { IMoniker } from 'core/naming';

export enum ManagedResourceStatus {
  ACTUATING = 'ACTUATING',
  CREATED = 'CREATED',
  DIFF = 'DIFF',
  ERROR = 'ERROR',
  HAPPY = 'HAPPY',
  PAUSED = 'PAUSED',
  UNHAPPY = 'UNHAPPY',
  UNKNOWN = 'UNKNOWN',
}

export interface IManagedResourceSummary {
  id: string;
  kind: string;
  status: ManagedResourceStatus;
  moniker: IMoniker;
  locations: {
    account: string;
    regions: Array<{ name: string }>;
  };
}

export interface IManagedApplicationSummary {
  hasManagedResources: boolean;
  resources: IManagedResourceSummary[];
}

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
