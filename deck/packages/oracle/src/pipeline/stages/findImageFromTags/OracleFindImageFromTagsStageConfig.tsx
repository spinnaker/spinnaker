import React, { useEffect, useState } from 'react';

import type { IStageConfigProps } from '@spinnaker/core';
import {
  BakeryReader,
  ChecklistInput,
  ExecutionDetailsTasks,
  MapEditor,
  Registry,
  StageConfigField,
} from '@spinnaker/core';

export function OracleFindImageFromTagsStageConfig({ pipeline, stage, updateStageField }: IStageConfigProps) {
  const [regions, setRegions] = useState<string[]>([]);

  useEffect(() => {
    const changes: Record<string, any> = {};
    if (stage.cloudProvider !== 'oracle') {
      changes.cloudProvider = 'oracle';
    }
    if (!stage.packageName) {
      changes.packageName = '*';
    }
    if (!stage.tags) {
      changes.tags = {};
    }
    if (!stage.regions) {
      changes.regions = [];
    }
    if (Object.keys(changes).length) {
      updateStageField(changes);
    }
  }, []);

  useEffect(() => {
    let active = true;
    BakeryReader.getRegions('oracle').then((loadedRegions) => active && setRegions(loadedRegions));
    return () => {
      active = false;
    };
  }, []);

  return (
    <div className="form-horizontal">
      <StageConfigField label="Regions">
        <ChecklistInput
          inline={true}
          name="regions"
          onChange={(event: any) => updateStageField({ regions: event.target.value })}
          showSelectAll={true}
          stringOptions={regions}
          value={stage.regions || []}
        />
      </StageConfigField>
      <StageConfigField label="Pattern">
        <input
          className="form-control input-sm"
          onChange={(event) => updateStageField({ packageName: event.target.value })}
          value={stage.packageName || ''}
        />
      </StageConfigField>
      <StageConfigField label="Tags">
        <MapEditor
          allowEmpty={true}
          model={stage.tags || {}}
          onChange={(tags) => updateStageField({ tags })}
          pipeline={pipeline}
        />
      </StageConfigField>
    </div>
  );
}

export const oracleFindImageFromTagsStage = {
  key: 'findImageFromTags',
  provides: 'findImageFromTags',
  cloudProvider: 'oracle',
  component: OracleFindImageFromTagsStageConfig,
  executionDetailsSections: [ExecutionDetailsTasks],
  validators: [
    { type: 'requiredField', fieldName: 'regions' },
    { type: 'requiredField', fieldName: 'packageName' },
  ],
};

export function registerOracleFindImageFromTagsStage() {
  Registry.pipeline.registerStage(oracleFindImageFromTagsStage);
}

registerOracleFindImageFromTagsStage();
