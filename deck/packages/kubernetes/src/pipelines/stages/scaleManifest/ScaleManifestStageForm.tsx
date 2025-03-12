import React from 'react';

import type { IFormikStageConfigInjectedProps } from '@spinnaker/core';

import type { IScaleCommand } from '../../../manifest';
import { ScaleSettingsForm } from '../../../manifest/scale/ScaleSettingsForm';
import type { IManifestSelector } from '../../../manifest/selector/IManifestSelector';
import { SelectorMode } from '../../../manifest/selector/IManifestSelector';
import { ManifestSelector } from '../../../manifest/selector/ManifestSelector';

interface IScaleManifestStageConfigFormProps {
  stageFieldUpdated: () => void;
}

export function ScaleManifestStageForm({
  application,
  formik,
  stageFieldUpdated,
}: IScaleManifestStageConfigFormProps & IFormikStageConfigInjectedProps) {
  const stage = formik.values;

  const onManifestSelectorChange = () => {
    stageFieldUpdated();
  };

  const onScaleSettingsFormChange = () => {
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
      <h4>Settings</h4>
      <div className="horizontal-rule" />
      <ScaleSettingsForm options={(stage as unknown) as IScaleCommand} onChange={onScaleSettingsFormChange} />
    </div>
  );
}
