import { INotificationTypeConfig } from 'core/domain';

import { MicrosoftTeamsNotificationType } from './MicrosoftTeamsNotificationType';

export const microsoftTeamsNotification: INotificationTypeConfig = {
  component: MicrosoftTeamsNotificationType,
  key: 'microsoftteams',
  label: 'Microsoft Teams',
};
