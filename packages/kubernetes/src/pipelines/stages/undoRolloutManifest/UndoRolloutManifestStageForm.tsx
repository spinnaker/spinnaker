import React from 'react';

import type { IFormikStageConfigInjectedProps } from '@spinnaker/core';
import { NumberInput, StageConfigField } from '@spinnaker/core';

import type { IManifestSelector } from '../../../manifest/selector/IManifestSelector';
import { SelectorMode } from '../../../manifest/selector/IManifestSelector';
import { ManifestSelector } from '../../../manifest/selector/ManifestSelector';

interface IUndoRolloutManifestStageConfigFormProps {
  stageFieldUpdated: () => void;
}

export function UndoRolloutManifestStageForm({
  application,
  formik,
  stageFieldUpdated,
}: IUndoRolloutManifestStageConfigFormProps & IFormikStageConfigInjectedProps) {
  const stage = formik.values;

  const onManifestSelectorChange = () => {
    stageFieldUpdated();
  };

  const onRevisionsChange = (e: React.ChangeEvent<any>) => {
    formik.setFieldValue('numRevisionsBack', e.target.value);
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
      <StageConfigField
        label="Revisions Back"
        helpKey="kubernetes.manifest.undoRollout.revisionsBack"
        fieldColumns={4}
        groupClassName="form-group form-inline"
      >
        <div className="input-group">
          <NumberInput
            inputClassName="input-sm highlight-pristine"
            onChange={onRevisionsChange}
            value={stage.numRevisionsBack}
            min={1}
          />
          <span className="input-group-addon">{stage.numRevisionsBack === '1' ? 'revision' : 'revisions'}</span>
        </div>
      </StageConfigField>
    </div>
  );
}
