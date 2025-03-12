import { GithubNotificationType } from './GithubNotificationType';
import type { INotificationTypeConfig } from '../../../../domain';

export const githubstatusNotification: INotificationTypeConfig = {
  component: GithubNotificationType,
  key: 'githubStatus',
  label: 'Github Status',
};
