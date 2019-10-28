import { IPromise } from 'angular';

import { API } from 'core/api';
import { IMoniker } from 'core/naming';

export enum ManagedResourceStatus {
  ACTUATING = 'ACTUATING',
  DIFF = 'DIFF',
  ERROR = 'ERROR',
  HAPPY = 'HAPPY',
  UNHAPPY = 'UNHAPPY',
}

export interface IManagedResourceSummary {
  id: string;
  kind: string;
  status: ManagedResourceStatus;
  moniker: IMoniker;
  locations: {
    accountName: string;
    regions: string[];
  };
}

export interface IManagedApplicationSummary {
  hasManagedResources: boolean;
  resources: IManagedResourceSummary[];
}

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
