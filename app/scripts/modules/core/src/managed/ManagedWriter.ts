import { IPromise } from 'angular';

import { API } from 'core/api';

export class ManagedWriter {
  public static pauseResourceManagement(application: string): IPromise<void> {
    return API.one('managed')
      .one('application', application)
      .one('pause')
      .post();
  }

  public static resumeResourceManagement(application: string): IPromise<void> {
    return API.one('managed')
      .one('application', application)
      .one('pause')
      .remove();
  }
}
