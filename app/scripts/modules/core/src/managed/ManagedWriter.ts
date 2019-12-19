import { IPromise } from 'angular';

import { API } from 'core/api';

export class ManagedWriter {
  public static pauseApplicationManagement(applicationName: string): IPromise<void> {
    return API.one('managed')
      .one('application', applicationName)
      .one('pause')
      .post();
  }

  public static resumeApplicationManagement(applicationName: string): IPromise<void> {
    return API.one('managed')
      .one('application', applicationName)
      .one('pause')
      .remove();
  }

  public static pauseResourceManagement(resourceId: string): IPromise<void> {
    return API.one('managed')
      .one('resources', resourceId)
      .one('pause')
      .post();
  }

  public static resumeResourceManagement(resourceId: string): IPromise<void> {
    return API.one('managed')
      .one('resources', resourceId)
      .one('pause')
      .remove();
  }
}
