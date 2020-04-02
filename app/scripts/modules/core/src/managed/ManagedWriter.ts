import { IPromise } from 'angular';

import { API } from 'core/api';
import { StatefulConstraintStatus } from 'core/domain';

export interface IUpdateConstraintStatusRequest {
  application: string;
  environment: string;
  type: string;
  version: string;
  status: StatefulConstraintStatus;
}

export class ManagedWriter {
  public static updateConstraintStatus({
    application,
    environment,
    type,
    version,
    status,
  }: IUpdateConstraintStatusRequest): IPromise<void> {
    return API.one('managed')
      .one('application', application)
      .one('environment', environment)
      .one('constraint')
      .post({
        type,
        artifactVersion: version,
        status,
      });
  }

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
