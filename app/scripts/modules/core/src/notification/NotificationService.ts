import { API } from 'core/api/ApiService';

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
    return API.path('notifications').path('metadata').useCache().get();
  }
}
