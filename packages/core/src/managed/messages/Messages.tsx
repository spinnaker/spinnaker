import React from 'react';

import { MessageBox, MessagesSection } from './MessageBox';
import { RelativeTimestamp } from '../RelativeTimestamp';
import { ManagementWarning } from '../config/ManagementWarning';
import type { FetchNotificationsQueryVariables } from '../graphql/graphql-sdk';
import {
  FetchNotificationsDocument,
  useDismissNotificationMutation,
  useFetchNotificationsQuery,
} from '../graphql/graphql-sdk';
import { useApplicationContextSafe } from '../../presentation';
import { getIsDebugMode } from '../utils/debugMode';
import { useLogEvent } from '../utils/logging';

const AppNotifications = () => {
  const app = useApplicationContextSafe();
  const logEvent = useLogEvent('Error', 'AppNotifications');
  const variables: FetchNotificationsQueryVariables = { appName: app.name };
  const { data, error } = useFetchNotificationsQuery({ variables });
  const [onDismiss] = useDismissNotificationMutation({
    refetchQueries: [{ query: FetchNotificationsDocument, variables }],
  });

  React.useEffect(() => {
    if (error) {
      logEvent({ level: 'ERROR', error });
    }
  }, [error, logEvent]);

  const notifications = data?.application?.notifications || [];
  const isDebug = getIsDebugMode();

  if (!notifications.length) return null;

  return (
    <MessagesSection>
      {notifications.map((notification) => (
        <MessageBox
          key={notification.id}
          type={notification.level}
          onDismiss={
            isDebug
              ? () => onDismiss({ variables: { payload: { application: app.name, id: notification.id } } })
              : undefined
          }
        >
          {notification.message}{' '}
          {notification.triggeredAt && (
            <>
              (<RelativeTimestamp timestamp={notification.triggeredAt} withSuffix />)
            </>
          )}
          {notification.link && (
            <>
              {' '}
              -{' '}
              <a href={notification.link} target="_blank">
                View
              </a>
            </>
          )}
        </MessageBox>
      ))}
    </MessagesSection>
  );
};

interface IMessagesProps {
  showManagementWarning?: boolean;
}

export const Messages = ({ showManagementWarning = true }: IMessagesProps) => {
  const app = useApplicationContextSafe();
  return (
    <>
      <AppNotifications />
      {showManagementWarning && <ManagementWarning appName={app.name} />}
    </>
  );
};
