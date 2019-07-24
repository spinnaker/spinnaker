import * as React from 'react';

import { IFormInputProps, ReactSelectInput } from 'core/presentation';
import { useLatestPromise } from 'core/presentation/forms/useLatestPromise';
import { ServiceAccountReader } from 'core/serviceAccount';

export function RunAsUserInput(props: IFormInputProps) {
  const [serviceAccounts, status] = useLatestPromise(() => ServiceAccountReader.getServiceAccounts(), []);
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
