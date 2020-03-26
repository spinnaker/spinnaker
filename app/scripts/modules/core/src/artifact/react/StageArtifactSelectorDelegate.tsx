import React from 'react';

import { StageConfigField } from 'core/pipeline';

import { IStageArtifactSelectorProps, StageArtifactSelector } from './StageArtifactSelector';
import { IPreRewriteArtifactSelectorProps, PreRewriteStageArtifactSelector } from './PreRewriteStageArtifactSelector';
import { ArtifactsMode, ArtifactsModeService } from '../ArtifactsModeService';

interface IStageArtifactSelectorDelegateProps {
  helpKey?: string;
  label: string;
  fieldColumns?: number;
}

export const StageArtifactSelectorDelegate = (
  props: IStageArtifactSelectorProps & IPreRewriteArtifactSelectorProps & IStageArtifactSelectorDelegateProps,
) => {
  return ArtifactsModeService.artifactsMode === ArtifactsMode.STANDARD ? (
    <StageConfigField label={props.label} helpKey={props.helpKey} fieldColumns={props.fieldColumns}>
      <StageArtifactSelector {...props} />
    </StageConfigField>
  ) : (
    <PreRewriteStageArtifactSelector {...props} />
  );
};
