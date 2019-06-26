import * as React from 'react';

import { StageConfigField } from 'core/pipeline';
import { SETTINGS } from 'core/config/settings';

import { StageArtifactSelector, IStageArtifactSelectorProps } from './StageArtifactSelector';
import { PreRewriteStageArtifactSelector, IPreRewriteArtifactSelectorProps } from './PreRewriteStageArtifactSelector';

interface IStageArtifactSelectorDelegateProps {
  helpKey?: string;
  label: string;
  fieldColumns?: number;
}

export const StageArtifactSelectorDelegate = (
  props: IStageArtifactSelectorProps & IPreRewriteArtifactSelectorProps & IStageArtifactSelectorDelegateProps,
) => {
  return SETTINGS.feature['artifactsRewrite'] ? (
    <StageConfigField label={props.label} helpKey={props.helpKey} fieldColumns={props.fieldColumns}>
      <StageArtifactSelector {...props} />
    </StageConfigField>
  ) : (
    <PreRewriteStageArtifactSelector {...props} />
  );
};
