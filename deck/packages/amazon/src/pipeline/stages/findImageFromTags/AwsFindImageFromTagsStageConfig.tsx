import React from 'react';

import type { IStageConfigProps } from '@spinnaker/core';
import { BakeryReader, ChecklistInput, MapEditor, StageConfigField } from '@spinnaker/core';

export function AwsFindImageFromTagsStageConfig({ application, pipeline, stage, updateStageField }: IStageConfigProps) {
  const [regions, setRegions] = React.useState<string[]>([]);
  const regionOptions = [...(stage.regions ?? []), ...regions].filter(
    (region, index, allRegions) => region && allRegions.indexOf(region) === index,
  );

  React.useEffect(() => {
    const defaults: { cloudProvider?: string; regions?: string[]; tags?: Record<string, string> } = {};
    if (stage.cloudProvider === undefined) {
      defaults.cloudProvider = 'aws';
    }
    if (stage.regions === undefined) {
      defaults.regions = application.defaultRegions?.aws ? [application.defaultRegions.aws] : [];
    }
    if (stage.tags === undefined) {
      defaults.tags = {};
    }
    if (Object.keys(defaults).length > 0) {
      updateStageField(defaults);
    }
  }, []);

  React.useEffect(() => {
    let mounted = true;
    BakeryReader.getRegions('aws').then((loadedRegions) => {
      if (mounted) {
        setRegions(loadedRegions);
      }
    });
    return () => {
      mounted = false;
    };
  }, []);

  return (
    <div className="form-horizontal">
      <StageConfigField label="Package">
        <input
          className="form-control input-sm"
          name="packageName"
          onChange={(event) => updateStageField({ packageName: event.target.value })}
          type="text"
          value={stage.packageName ?? ''}
        />
      </StageConfigField>
      <StageConfigField label="Regions">
        <ChecklistInput
          inline={true}
          name="regions"
          onChange={(event: React.ChangeEvent<HTMLInputElement>) => updateStageField({ regions: event.target.value })}
          showSelectAll={true}
          stringOptions={regionOptions}
          value={stage.regions ?? []}
        />
      </StageConfigField>
      <StageConfigField label="Tags">
        <MapEditor
          allowEmpty={true}
          model={stage.tags ?? {}}
          onChange={(tags) => updateStageField({ tags })}
          pipeline={pipeline}
        />
      </StageConfigField>
    </div>
  );
}
