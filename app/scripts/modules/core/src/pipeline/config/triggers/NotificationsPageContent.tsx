import { INotification, IPipeline } from 'core/domain';
import { NotificationsList } from 'core/notification';
import React from 'react';

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
