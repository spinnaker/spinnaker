import { REST } from '../api/ApiService';
import { INotification } from '../domain';

export interface IAppNotifications {
  application: string;
  [key: string]: INotification[] | string; // "| string" is just for application field
}

export class AppNotificationsService {
  public static getNotificationsForApplication(applicationName: string): PromiseLike<IAppNotifications> {
    return REST('/notifications/application').path(applicationName).get();
  }

  public static saveNotificationsForApplication(
    applicationName: string,
    notifications: IAppNotifications,
  ): PromiseLike<void> {
    return REST('/notifications/application').path(applicationName).post(notifications);
  }
}
