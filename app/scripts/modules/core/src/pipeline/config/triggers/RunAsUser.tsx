import React from 'react';

import { IFormInputProps, ReactSelectInput, useLatestPromise } from 'core/presentation';
import { ServiceAccountReader } from 'core/serviceAccount';

export function RunAsUserInput(props: IFormInputProps) {
  const fetchServiceAccounts = useLatestPromise(() => ServiceAccountReader.getServiceAccounts(), []);
  const isLoading = fetchServiceAccounts.status === 'PENDING';

  return (
    <ReactSelectInput
      {...props}
      isLoading={isLoading}
      stringOptions={fetchServiceAccounts.result || []}
      placeholder="Select Run As User"
    />
  );
}
