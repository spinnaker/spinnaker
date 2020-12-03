import { API } from 'core/api/ApiService';
import { INotification } from 'core/domain';

export interface IAppNotifications {
  application: string;
  [key: string]: INotification[] | string; // "| string" is just for application field
}

export class AppNotificationsService {
  public static getNotificationsForApplication(applicationName: string): PromiseLike<IAppNotifications> {
    return API.path('notifications', 'application', applicationName).get();
  }

  public static saveNotificationsForApplication(
    applicationName: string,
    notifications: IAppNotifications,
  ): PromiseLike<void> {
    return API.path('notifications', 'application', applicationName).post(notifications);
  }
}
