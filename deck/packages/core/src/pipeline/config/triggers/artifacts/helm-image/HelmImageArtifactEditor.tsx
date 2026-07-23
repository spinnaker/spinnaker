import { cloneDeep } from 'lodash';
import React from 'react';

import { ArtifactEditor } from '../ArtifactEditor';
import { ArtifactTypePatterns } from '../../../../../artifact/ArtifactTypes';
import type { IArtifact, IArtifactEditorProps, IArtifactKindConfig } from '../../../../../domain';
import { singleFieldArtifactEditor } from '../singleFieldArtifactEditor';
import { StageConfigField } from '../../../stages/common';
import { SpelText } from '../../../../../widgets/spelText/SpelText';

const TYPE = 'helm/image';

const setNameAndVersionFromReference = (artifact: IArtifact) => {
  const ref = artifact.reference;
  if (ref == null) {
    return artifact;
  }

  const atIndex = ref.indexOf('@');
  const lastColonIndex = ref.lastIndexOf(':');

  if (atIndex >= 0) {
    const split = ref.split('@');
    artifact.name = split[0];
    artifact.version = split[1];
  } else if (lastColonIndex > 0) {
    artifact.name = ref.substring(0, lastColonIndex);
    artifact.version = ref.substring(lastColonIndex + 1);
  } else {
    artifact.name = ref;
  }
  return artifact;
};

export const HelmImageMatch: IArtifactKindConfig = {
  label: 'Docker Image (OCI)',
  typePattern: ArtifactTypePatterns.HELM_IMAGE,
  type: TYPE,
  isDefault: false,
  isMatch: true,
  description: 'A Docker image stored in an OCI registry.',
  key: 'helm-image',
  editCmp: singleFieldArtifactEditor(
    'name',
    TYPE,
    'Docker image',
    'gcr.io/project/image',
    'pipeline.config.expectedArtifact.docker.name',
  ),
};

export const HelmImageDefault: IArtifactKindConfig = {
  label: 'Docker Image (OCI)',
  typePattern: ArtifactTypePatterns.HELM_IMAGE,
  type: TYPE,
  isDefault: true,
  isMatch: false,
  description: 'A Docker image stored in an OCI registry.',
  key: 'default.helm-image',
  editCmp: class extends ArtifactEditor {
    constructor(props: IArtifactEditorProps) {
      super(props, TYPE);
    }

    private onReferenceChanged = (reference: string) => {
      const clonedArtifact = cloneDeep(this.props.artifact);
      clonedArtifact.reference = reference;
      this.props.onChange(setNameAndVersionFromReference(clonedArtifact));
    };

    public render() {
      return (
        <StageConfigField label="Object path" helpKey="pipeline.config.expectedArtifact.defaultDocker.reference">
          <SpelText
            placeholder="gcr.io/project/image@sha256:9efcc2818c9..."
            value={this.props.artifact.reference}
            onChange={this.onReferenceChanged}
            pipeline={this.props.pipeline}
            docLink={true}
          />
        </StageConfigField>
      );
    }
  },
};
