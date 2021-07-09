import { cloneDeep } from 'lodash';
import React from 'react';

import { ArtifactEditor } from '../ArtifactEditor';
import { ArtifactTypePatterns } from '../../../../../artifact';
import { IArtifactEditorProps, IArtifactKindConfig } from '../../../../../domain';
import { singleFieldArtifactEditor } from '../singleFieldArtifactEditor';
import { StageConfigField } from '../../../stages/common';
import { SpelText } from '../../../../../widgets';

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
      const clonedArtifact = cloneDeep(this.props.artifact);
      clonedArtifact.reference = reference;
      this.props.onChange(clonedArtifact);
    };

    private onFilePathChanged = (name: string) => {
      const clonedArtifact = cloneDeep(this.props.artifact);
      clonedArtifact.name = name;
      this.props.onChange(clonedArtifact);
    };

    public render() {
      return (
        <>
          <StageConfigField label="Object path" helpKey="pipeline.config.expectedArtifact.defaultBitbucket.reference">
            <SpelText
              placeholder="https://api.bitbucket.com/rest/api/1.0/$PROJECTS/$PROJECTKEY/repos/$REPONAME/raw/$FILEPATH"
              value={this.props.artifact.reference}
              onChange={this.onReferenceChanged}
              pipeline={this.props.pipeline}
              docLink={true}
            />
          </StageConfigField>
          <StageConfigField label="File Path" helpKey="pipeline.config.expectedArtifact.defaultBitbucket.filepath">
            <SpelText
              placeholder="path/to/file.yml"
              onChange={this.onFilePathChanged}
              value={this.props.artifact.name}
              pipeline={this.props.pipeline}
              docLink={true}
            />
          </StageConfigField>
        </>
      );
    }
  },
};
