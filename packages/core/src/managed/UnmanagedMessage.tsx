import React from 'react';

import { SETTINGS } from '../config';

const defaultGettingStartedUrl = 'https://www.spinnaker.io/guides/user/managed-delivery/getting-started/';

export const UnmanagedMessage = () => {
  const gettingStartedLink = SETTINGS.managedDelivery?.gettingStartedUrl || defaultGettingStartedUrl;
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
