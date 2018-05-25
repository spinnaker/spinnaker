import { IPromise } from 'angular';

import { API } from 'core/api/ApiService';
import { IAppNotification } from 'core/domain';

export interface IAppNotifications {
  application: string;
  [key: string]: IAppNotification[] | string; // "| string" is just for application field
}

export class AppNotificationsService {
  public static getNotificationsForApplication(applicationName: string): IPromise<IAppNotifications> {
    return API.one('notifications')
      .one('application', applicationName)
      .get();
  }

  public static saveNotificationsForApplication(
    applicationName: string,
    notifications: IAppNotifications,
  ): IPromise<void> {
    return API.one('notifications')
      .one('application', applicationName)
      .data(notifications)
      .post();
  }
}
