import { SlackNotificationType } from './SlackNotificationType';
import type { INotificationTypeConfig } from '../../../../domain';

export const slackNotification: INotificationTypeConfig = {
  component: SlackNotificationType,
  key: 'slack',
  label: 'Slack',
};
