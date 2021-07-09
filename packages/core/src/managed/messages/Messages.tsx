import React from 'react';

import { MessageBox, MessagesSection } from './MessageBox';
import { RelativeTimestamp } from '../RelativeTimestamp';
import { ManagementWarning } from '../config/ManagementWarning';
import { useFetchNotificationsQuery } from '../graphql/graphql-sdk';
import { useApplicationContextSafe } from '../../presentation';
import { useLogEvent } from '../utils/logging';

const AppNotifications = () => {
  const app = useApplicationContextSafe();
  const logEvent = useLogEvent('Error', 'AppNotifications');
  const { data, error } = useFetchNotificationsQuery({ variables: { appName: app.name } });

  React.useEffect(() => {
    if (error) {
      logEvent({ level: 'ERROR', error });
    }
  }, [error, logEvent]);

  const notifications = data?.application?.notifications || [];

  if (!notifications.length) return null;

  return (
    <MessagesSection>
      {notifications.map((notification) => (
        <MessageBox key={notification.id} type={notification.level}>
          {notification.message}{' '}
          {notification.triggeredAt && (
            <>
              (<RelativeTimestamp timestamp={notification.triggeredAt} />)
            </>
          )}
        </MessageBox>
      ))}
    </MessagesSection>
  );
};

export const Messages = () => {
  const app = useApplicationContextSafe();
  return (
    <>
      <AppNotifications />
      <ManagementWarning appName={app.name} />
    </>
  );
};
