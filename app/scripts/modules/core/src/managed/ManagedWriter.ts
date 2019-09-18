import { IPromise } from 'angular';

import { API } from 'core/api';

export class ManagedWriter {
  public static pauseResourceManagement(application: string): IPromise<void> {
    return API.one('managed')
      .all('vetos')
      .one('ApplicationVeto')
      .data({ application, optedOut: true })
      .post();
  }

  public static resumeResourceManagement(application: string): IPromise<void> {
    return API.one('managed')
      .all('vetos')
      .one('ApplicationVeto')
      .data({ application, optedOut: false })
      .post();
  }
}
