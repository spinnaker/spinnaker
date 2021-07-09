import { ApolloError } from '@apollo/client';
import React from 'react';
import { UnmanagedMessage } from './UnmanagedMessage';
import { useLogEvent } from './utils/logging';

interface IApplicationQueryErrorProps {
  hasApplicationData: boolean;
  error: ApolloError;
}

export const ApplicationQueryError = ({ hasApplicationData, error }: IApplicationQueryErrorProps) => {
  const logEvent = useLogEvent('Error', 'AppQuery');
  React.useEffect(() => {
    if (error && hasApplicationData) {
      // Log events except for un-managed apps
      logEvent({ level: 'ERROR', error });
    }
  }, [error, logEvent, hasApplicationData]);

  if (!hasApplicationData) {
    return <UnmanagedMessage />;
  }

  return (
    <div style={{ width: '100%' }}>
      Failed to load environments data, please refresh and try again.
      <p>{error.message}</p>
    </div>
  );
};
