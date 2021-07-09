import { cloneDeep, isNil } from 'lodash';
import React from 'react';

import { ArtifactEditor } from '../ArtifactEditor';
import { ArtifactTypePatterns } from '../../../../../artifact';
import { IArtifact, IArtifactEditorProps, IArtifactKindConfig } from '../../../../../domain';
import { singleFieldArtifactEditor } from '../singleFieldArtifactEditor';
import { StageConfigField } from '../../../stages/common';
import { SpelText } from '../../../../../widgets';

const TYPE = 'docker/image';

export const setNameAndVersionFromReference = (artifact: IArtifact) => {
  const ref = artifact.reference;
  if (isNil(ref)) {
    return artifact;
  }

  const atIndex: number = ref.indexOf('@');
  const lastColonIndex: number = ref.lastIndexOf(':');

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

export const DockerMatch: IArtifactKindConfig = {
  label: 'Docker',
  typePattern: ArtifactTypePatterns.DOCKER_IMAGE,
  type: TYPE,
  isDefault: false,
  isMatch: true,
  description: 'A Docker image to be deployed.',
  key: 'docker',
  editCmp: singleFieldArtifactEditor(
    'name',
    TYPE,
    'Docker image',
    'gcr.io/project/image',
    'pipeline.config.expectedArtifact.docker.name',
  ),
};

export const DockerDefault: IArtifactKindConfig = {
  label: 'Docker',
  typePattern: ArtifactTypePatterns.DOCKER_IMAGE,
  type: TYPE,
  isDefault: true,
  isMatch: false,
  description: 'A Docker image to be deployed.',
  key: 'default.docker',
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
