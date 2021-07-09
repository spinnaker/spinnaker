import { join } from 'lodash';

import { INotification } from '../domain';

export class NotificationTransformer {
  public static getNotificationWhenDisplayName = (whenOption: string, level?: string, stageType?: string): string => {
    if (stageType === 'manualJudgment') {
      let filteredInput = 'This stage ';

      if (whenOption === 'manualJudgment') {
        filteredInput += 'is awaiting judgment';
      } else {
        filteredInput += 'was judged to ' + whenOption.slice(14).toLowerCase();
      }

      return filteredInput;
    } else {
      whenOption = whenOption
        .replace('.', ' ')
        .replace('pipeline', (level === 'application' ? 'Any ' : 'This ') + 'pipeline is');
      whenOption = whenOption.replace('.', ' ').replace('stage', 'This stage is ');

      if (whenOption.includes('failed')) {
        whenOption = whenOption.replace('pipeline is', 'pipeline has');
        whenOption = whenOption.replace('stage is', 'stage has');
      }

      return whenOption;
    }
  };

  public static getNotificationDetails = (notification: INotification): string => {
    if (notification.type === 'pubsub') {
      return 'Publisher Name: ' + notification.publisherName;
    } else if (notification.type !== 'email') {
      return notification.address;
    } else {
      const addresses = [];
      if (notification.address) {
        addresses.push(notification.address);
      }
      if (notification.cc) {
        addresses.push('cc:' + notification.cc);
      }
      return join(addresses, ', ');
    }
  };
}
