import React from 'react';
import { getDocsUrl } from './utils/defaults';

export const UnmanagedMessage = () => {
  const gettingStartedLink = getDocsUrl('gettingStarted');
  return (
    <div style={{ width: '100%' }}>
      Welcome! This application does not have any environments or artifacts. Check out the{' '}
      <a href={gettingStartedLink} target="_blank">
        getting started guide
      </a>{' '}
      to set some up!
    </div>
  );
};
