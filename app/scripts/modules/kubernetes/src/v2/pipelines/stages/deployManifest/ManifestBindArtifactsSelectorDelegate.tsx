import * as React from 'react';

import { SETTINGS } from '@spinnaker/core';

import { ManifestBindArtifactsSelector, IManifestBindArtifactsSelectorProps } from './ManifestBindArtifactsSelector';
import {
  PreRewriteManifestBindArtifactSelector,
  IExpectedArtifactMultiSelectorProps,
} from './PreRewriteManifestBindArtifactSelector';

export type IManifestBindArtifactsSelectorDelegateProps = IManifestBindArtifactsSelectorProps &
  IExpectedArtifactMultiSelectorProps;

export const ManifestBindArtifactsSelectorDelegate = (props: IManifestBindArtifactsSelectorDelegateProps) => {
  return SETTINGS.feature['artifactsRewrite'] ? (
    <ManifestBindArtifactsSelector {...props} />
  ) : (
    <PreRewriteManifestBindArtifactSelector {...props} />
  );
};
