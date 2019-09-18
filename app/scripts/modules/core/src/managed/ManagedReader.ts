import { IPromise } from 'angular';

import { API } from 'core/api';

export interface IManagedApplicationSummary {
  hasManagedResources: boolean;
}

export class ManagedReader {
  public static getApplicationSummary(app: string): IPromise<IManagedApplicationSummary> {
    return API.one('managed')
      .one('application', app)
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
