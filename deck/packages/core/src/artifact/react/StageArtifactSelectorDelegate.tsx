import React from 'react';

import type { IStageArtifactSelectorProps } from './StageArtifactSelector';
import { StageArtifactSelector } from './StageArtifactSelector';
import { StageConfigField } from '../../pipeline/config/stages/common/stageConfigField/StageConfigField';

interface IStageArtifactSelectorDelegateProps {
  helpKey?: string;
  label: string;
  fieldColumns?: number;
}

/** @deprecated use StageArtifactSelector instead */
export const StageArtifactSelectorDelegate = (
  props: IStageArtifactSelectorProps & IStageArtifactSelectorDelegateProps,
) => {
  return (
    <StageConfigField label={props.label} helpKey={props.helpKey} fieldColumns={props.fieldColumns}>
      <StageArtifactSelector {...props} />
    </StageConfigField>
  );
};
