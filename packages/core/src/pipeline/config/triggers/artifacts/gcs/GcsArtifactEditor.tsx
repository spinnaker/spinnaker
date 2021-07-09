import { cloneDeep, isNil } from 'lodash';
import React from 'react';

import { ArtifactEditor } from '../ArtifactEditor';
import { ArtifactTypePatterns } from '../../../../../artifact';
import { IArtifactEditorProps, IArtifactKindConfig } from '../../../../../domain';
import { singleFieldArtifactEditor } from '../singleFieldArtifactEditor';
import { StageConfigField } from '../../../stages/common';
import { SpelText } from '../../../../../widgets';

const TYPE = 'gcs/object';

export const GcsMatch: IArtifactKindConfig = {
  label: 'GCS',
  typePattern: ArtifactTypePatterns.GCS_OBJECT,
  type: TYPE,
  description: 'A GCS object.',
  key: 'gcs',
  isDefault: false,
  isMatch: true,
  editCmp: singleFieldArtifactEditor(
    'name',
    TYPE,
    'Object path',
    'gs://bucket/path/to/file',
    'pipeline.config.expectedArtifact.gcs.name',
  ),
};

export const GcsDefault: IArtifactKindConfig = {
  label: 'GCS',
  typePattern: ArtifactTypePatterns.GCS_OBJECT,
  type: TYPE,
  description: 'A GCS object.',
  key: 'default.gcs',
  isDefault: true,
  isMatch: false,
  editCmp: class extends ArtifactEditor {
    constructor(props: IArtifactEditorProps) {
      super(props, TYPE);
    }

    private onReferenceChange = (reference: string) => {
      if (isNil(reference)) {
        return;
      }

      const clonedArtifact = cloneDeep(this.props.artifact);
      clonedArtifact.reference = reference;

      if (reference.indexOf('#') >= 0) {
        const split = reference.split('#');
        clonedArtifact.name = split[0];
        clonedArtifact.version = split[1];
      } else {
        clonedArtifact.name = reference;
      }
      this.props.onChange(clonedArtifact);
    };

    public render() {
      return (
        <StageConfigField label="Object path" helpKey="pipeline.config.expectedArtifact.defaultGcs.reference">
          <SpelText
            placeholder="gs://bucket/path/to/file"
            value={this.props.artifact.reference}
            onChange={this.onReferenceChange}
            pipeline={this.props.pipeline}
            docLink={true}
          />
        </StageConfigField>
      );
    }
  },
};
