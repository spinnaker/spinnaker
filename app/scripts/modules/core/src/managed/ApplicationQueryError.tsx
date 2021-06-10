import { ApolloError } from '@apollo/client';
import React from 'react';
import { UnmanagedMessage } from './UnmanagedMessage';

interface IApplicationQueryErrorProps {
  hasApplicationData: boolean;
  error: ApolloError;
}

export const ApplicationQueryError = ({ hasApplicationData, error }: IApplicationQueryErrorProps) => {
  if (!hasApplicationData) {
    return <UnmanagedMessage />;
  }
  console.warn(error);
  return (
    <div style={{ width: '100%' }}>
      Failed to load environments data, please refresh and try again.
      <p>{error.message}</p>
    </div>
  );
};
