import { INotificationTypeConfig } from 'core/domain';

import { SlackNotificationType } from './SlackNotificationType';

export const slackNotification: INotificationTypeConfig = {
  component: SlackNotificationType,
  key: 'slack',
  label: 'Slack',
};
