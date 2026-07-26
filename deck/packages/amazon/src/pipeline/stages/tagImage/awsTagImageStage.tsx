import React from 'react';

import type { IStageConfigProps, IStageTypeConfig } from '@spinnaker/core';
import { MapEditor, PipelineConfigService, Registry, StageConfigField, StageConstants } from '@spinnaker/core';

type StageRefId = string | number;

function toggleConsideredStage(current: StageRefId[], refId: StageRefId, checked: boolean): StageRefId[] {
  if (checked && !current.includes(refId)) {
    return [...current, refId];
  }
  if (!checked) {
    return current.filter((candidate) => candidate !== refId);
  }
  return current;
}

export function AwsTagImageStageConfig({ pipeline, stage, updateStageField }: IStageConfigProps) {
  const consideredStages: StageRefId[] = stage.consideredStages || [];
  const imageProducingStages = PipelineConfigService.getAllUpstreamDependencies(
    pipeline,
    stage,
  ).filter((upstreamStage) => StageConstants.IMAGE_PRODUCING_STAGES.includes(upstreamStage.type));
  const availableRefIds = new Set(imageProducingStages.map((upstreamStage) => upstreamStage.refId));
  const staleRefIds = consideredStages.filter((refId) => !availableRefIds.has(refId));

  React.useEffect(() => {
    const defaults: Record<string, any> = {};
    if (stage.cloudProvider === undefined) {
      defaults.cloudProvider = 'aws';
    }
    if (stage.tags === undefined) {
      defaults.tags = {};
    }
    if (Object.keys(defaults).length) {
      updateStageField(defaults);
    }
  }, [stage.cloudProvider, stage.tags, updateStageField]);

  return (
    <div className="form-horizontal">
      <StageConfigField label="Tags">
        <MapEditor
          allowEmpty={true}
          model={stage.tags || {}}
          onChange={(tags) => updateStageField({ tags })}
          pipeline={pipeline}
        />
      </StageConfigField>
      <StageConfigField helpKey="aws.tagImage.consideredStages" label="Stages (optional)">
        <div className="checkbox">
          {imageProducingStages.map((upstreamStage) => (
            <label key={upstreamStage.refId} style={{ display: 'block' }}>
              <input
                checked={consideredStages.includes(upstreamStage.refId)}
                onChange={(event) =>
                  updateStageField({
                    consideredStages: toggleConsideredStage(
                      consideredStages,
                      upstreamStage.refId,
                      event.target.checked,
                    ),
                  })
                }
                type="checkbox"
                value={upstreamStage.refId}
              />{' '}
              {upstreamStage.name || upstreamStage.refId}
            </label>
          ))}
          {staleRefIds.map((refId) => (
            <label key={refId} style={{ display: 'block' }}>
              <input checked={true} disabled={true} readOnly={true} type="checkbox" value={refId} /> {refId}{' '}
              (unavailable)
            </label>
          ))}
        </div>
      </StageConfigField>
    </div>
  );
}

export const awsTagImageStage = {
  key: 'upsertImageTags',
  provides: 'upsertImageTags',
  cloudProvider: 'aws',
  component: AwsTagImageStageConfig,
  executionConfigSections: ['tagImageConfig', 'taskStatus'],
} as IStageTypeConfig;

export function registerAwsTagImageStage() {
  Registry.pipeline.registerStage(awsTagImageStage);
}

registerAwsTagImageStage();
