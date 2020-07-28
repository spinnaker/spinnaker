import { INotificationTypeConfig } from 'core/domain';

import { GithubNotificationType } from './GithubNotificationType';

export const githubstatusNotification: INotificationTypeConfig = {
  component: GithubNotificationType,
  key: 'githubStatus',
  label: 'Github Status',
};
