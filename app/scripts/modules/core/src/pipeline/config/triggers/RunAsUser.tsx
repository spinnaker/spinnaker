import * as React from 'react';

import { IFormInputProps, ReactSelectInput, useLatestPromise } from 'core/presentation';
import { ServiceAccountReader } from 'core/serviceAccount';

export function RunAsUserInput(props: IFormInputProps) {
  const { result: serviceAccounts, status } = useLatestPromise(() => ServiceAccountReader.getServiceAccounts(), []);
  const isLoading = status === 'PENDING';

  return (
    <ReactSelectInput
      {...props}
      isLoading={isLoading}
      stringOptions={serviceAccounts || []}
      placeholder="Select Run As User"
    />
  );
}
