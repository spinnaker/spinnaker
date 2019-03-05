import { cloneDeep } from 'lodash';
import * as React from 'react';

import { ArtifactTypePatterns } from 'core/artifact';
import { IArtifactEditorProps, IArtifactKindConfig } from 'core/domain';
import { StageConfigField } from 'core/pipeline';
import { SpelText } from 'core/widgets';

import { singleFieldArtifactEditor } from '../singleFieldArtifactEditor';
import { ArtifactEditor } from '../ArtifactEditor';

const TYPE = 'bitbucket/file';

export const BitbucketMatch: IArtifactKindConfig = {
  label: 'Bitbucket',
  typePattern: ArtifactTypePatterns.BITBUCKET_FILE,
  type: TYPE,
  description: 'A file stored in git, hosted by Bitbucket.',
  key: 'bitbucket',
  isDefault: false,
  isMatch: true,
  editCmp: singleFieldArtifactEditor(
    'name',
    TYPE,
    'File path',
    'manifests/frontend.yaml',
    'pipeline.config.expectedArtifact.git.name',
  ),
};

export const BitbucketDefault: IArtifactKindConfig = {
  label: 'Bitbucket',
  typePattern: ArtifactTypePatterns.BITBUCKET_FILE,
  type: TYPE,
  description: 'A file stored in git, hosted by Bitbucket.',
  key: 'default.bitbucket',
  isDefault: true,
  isMatch: false,
  editCmp: class extends ArtifactEditor {
    constructor(props: IArtifactEditorProps) {
      super(props, TYPE);
    }

    private onReferenceChanged = (reference: string) => {
      const pathRegex = new RegExp('/1.0/repositories/[^/]*/[^/]*/raw/[^/]*/(.*)$');
      const results = pathRegex.exec(reference);
      if (results !== null) {
        const clonedArtifact = cloneDeep(this.props.artifact);
        clonedArtifact.name = decodeURIComponent(results[1]);
        clonedArtifact.reference = reference;
        this.props.onChange(clonedArtifact);
      }
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
