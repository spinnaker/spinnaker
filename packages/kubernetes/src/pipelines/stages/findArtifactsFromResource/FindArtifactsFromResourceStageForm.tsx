import React from 'react';

import type { IFormikStageConfigInjectedProps } from '@spinnaker/core';

import type { IManifestSelector } from '../../../manifest/selector/IManifestSelector';
import { SelectorMode } from '../../../manifest/selector/IManifestSelector';
import { ManifestSelector } from '../../../manifest/selector/ManifestSelector';

interface IFindArtifactsFromResourceStageConfigFormProps {
  stageFieldUpdated: () => void;
}

export function FindArtifactsFromResourceStageForm({
  application,
  formik,
  stageFieldUpdated,
}: IFindArtifactsFromResourceStageConfigFormProps & IFormikStageConfigInjectedProps) {
  const stage = formik.values;

  const onManifestSelectorChange = () => {
    stageFieldUpdated();
  };

  return (
    <div className="form-horizontal">
      <h4>Manifest</h4>
      <div className="horizontal-rule" />
      <ManifestSelector
        application={application}
        selector={(stage as unknown) as IManifestSelector}
        modes={[SelectorMode.Static, SelectorMode.Dynamic]}
        onChange={onManifestSelectorChange}
        includeSpinnakerKinds={null}
      />
    </div>
  );
}
