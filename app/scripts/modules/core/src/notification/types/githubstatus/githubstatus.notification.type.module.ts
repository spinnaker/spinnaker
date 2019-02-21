import { module } from 'angular';

interface INotificationTypeConfig {
  label: string;
  key: string;
}

interface IRegisterNotificationTypeConfigProvider {
  registerNotificationType: (config: INotificationTypeConfig) => void;
}

export const SPINNAKER_CORE_NOTIFICATION_TYPES_GITHUB_STATUS = 'spinnaker.core.notification.types.githubstatus';
module(SPINNAKER_CORE_NOTIFICATION_TYPES_GITHUB_STATUS, []).config(
  ['notificationTypeConfigProvider', (notificationTypeConfigProvider: IRegisterNotificationTypeConfigProvider) => {
    notificationTypeConfigProvider.registerNotificationType({
      label: 'Github Status',
      key: 'githubStatus',
    });
  }],
);
