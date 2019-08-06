import * as React from 'react';

import { FormikFormField, ReactSelectInput, useLatestPromise } from 'core/presentation';

import { ArtifactoryReaderService } from './artifactoryReader.service';

export function ArtifactoryTrigger() {
  const fetchNames = useLatestPromise(() => ArtifactoryReaderService.getArtifactoryNames(), []);

  const validationStatus = fetchNames.status === 'REJECTED' ? 'error' : null;
  const validationMessage =
    status === 'REJECTED'
      ? `Error fetching artifactory names: ${fetchNames.error.data.status} ${fetchNames.error.data.error}`
      : null;

  return (
    <FormikFormField
      name="artifactorySearchName"
      label="Artifactory Name"
      touched={true}
      fastField={false}
      validationMessage={validationMessage}
      validationStatus={validationStatus}
      input={props => (
        <ReactSelectInput
          {...props}
          isLoading={status === 'PENDING'}
          placeholder="Select Artifactory search name..."
          stringOptions={fetchNames.result}
          clearable={false}
        />
      )}
    />
  );
}
