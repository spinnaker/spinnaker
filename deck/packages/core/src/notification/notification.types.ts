import { cloneDeep } from 'lodash';

import type { INotificationSettings } from '../config';
import { SETTINGS } from '../config';
import type { INotificationTypeConfig } from '../domain';
import { Registry } from '../registry';

import { cdEventsNotification } from './selector/types/cdevents/cdevents.notification';
import { emailNotification } from './selector/types/email/email.notification';
import { githubstatusNotification } from './selector/types/githubstatus/githubstatus.notification';
import { googlechatNotification } from './selector/types/googlechat/googlechat.notification';
import { microsoftTeamsNotification } from './selector/types/microsoftteams/microsoftteams.notification';
import { pubsubNotification } from './selector/types/pubsub/pubsub.notification';
import { slackNotification } from './selector/types/slack/slack.notification';
import { smsNotification } from './selector/types/sms/sms.notification';

const builtinNotificationTypes: INotificationTypeConfig[] = [
  emailNotification,
  githubstatusNotification,
  googlechatNotification,
  microsoftTeamsNotification,
  pubsubNotification,
  slackNotification,
  smsNotification,
  cdEventsNotification,
];

export const BUILTIN_NOTIFICATION_KEYS: ReadonlySet<string> = new Set(builtinNotificationTypes.map(({ key }) => key));

export function registerBuiltinNotificationTypes(): void {
  builtinNotificationTypes.forEach((config) => {
    if (SETTINGS.notifications) {
      const notificationSetting: { enabled: boolean; botName?: string } =
        SETTINGS.notifications[config.key as keyof INotificationSettings];

      if (
        notificationSetting?.enabled &&
        !Registry.pipeline.getNotificationTypes().some(({ key }) => key === config.key)
      ) {
        Registry.pipeline.registerNotification({
          config: { ...notificationSetting },
          ...cloneDeep(config),
        });
      }
    } else if (!Registry.pipeline.getNotificationTypes().some(({ key }) => key === config.key)) {
      Registry.pipeline.registerNotification(cloneDeep(config));
    }
  });
}
