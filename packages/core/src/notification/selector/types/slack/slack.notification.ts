import { SlackNotificationType } from './SlackNotificationType';
import { INotificationTypeConfig } from '../../../../domain';

export const slackNotification: INotificationTypeConfig = {
  component: SlackNotificationType,
  key: 'slack',
  label: 'Slack',
};
