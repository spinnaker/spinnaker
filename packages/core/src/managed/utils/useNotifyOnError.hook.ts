import type { ApolloError } from '@apollo/client';
import React from 'react';
import { NotifierService } from '../../widgets';

export const useNotifyOnError = ({
  key,
  content,
  error,
}: {
  key: string;
  content?: string;
  error: ApolloError | undefined;
}) => {
  React.useEffect(() => {
    if (!error) return;
    NotifierService.publish({
      key,
      content: content ? `${content} - ` : '' + error.message,
      options: { type: 'error' },
    });
  }, [error]);
};
