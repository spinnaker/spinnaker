import React from 'react';

import { INotification, IPipeline } from '../../../domain';
import { NotificationsList } from '../../../notification';

export interface INotificationsPageContentProps {
  pipeline: IPipeline;
  updatePipelineConfig: (changes: Partial<IPipeline>) => void;
}

export function NotificationsPageContent(props: INotificationsPageContentProps) {
  const { pipeline, updatePipelineConfig } = props;

  function updateNotification(notifications: INotification[]): void {
    updatePipelineConfig({ notifications });
  }

  return (
    <NotificationsList
      level={'pipeline'}
      stageType={pipeline.type}
      notifications={pipeline.notifications}
      updateNotifications={updateNotification}
    />
  );
}
