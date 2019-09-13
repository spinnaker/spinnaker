import * as React from 'react';

import { FormikFormField, ReactSelectInput, useLatestPromise } from 'core/presentation';

import { NexusReaderService } from './nexusReader.service';

export function NexusTrigger() {
  const fetchNames = useLatestPromise(() => NexusReaderService.getNexusNames(), []);

  const validationStatus = fetchNames.status === 'REJECTED' ? 'error' : null;
  const validationMessage =
    fetchNames.status === 'REJECTED'
      ? `Error fetching nexus names: ${fetchNames.error.data.status} ${fetchNames.error.data.error}`
      : null;

  return (
    <FormikFormField
      name="nexusSearchName"
      label="Nexus Name"
      touched={true}
      fastField={false}
      validationMessage={validationMessage}
      validationStatus={validationStatus}
      input={props => (
        <ReactSelectInput
          {...props}
          isLoading={status === 'PENDING'}
          placeholder="Select Nexus search name..."
          stringOptions={fetchNames.result}
          clearable={false}
        />
      )}
    />
  );
}
