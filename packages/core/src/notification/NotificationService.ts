import { REST } from '../api/ApiService';

export interface INotificationParameter {
  name: string;
  defaultValue: string;
  type: string;
  label: string;
  description: string;
}

export interface INotificationTypeMetadata {
  notificationType: string;
  parameters: INotificationParameter[];
  uiType: 'BASIC' | 'CUSTOM';
}

export class NotificationService {
  public static getNotificationTypeMetadata(): PromiseLike<INotificationTypeMetadata[]> {
    return REST('/notifications/metadata').useCache().get();
  }
}
