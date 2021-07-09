import { cloneDeep } from 'lodash';
import React from 'react';

import { ArtifactEditor } from '../ArtifactEditor';
import { ArtifactTypePatterns } from '../../../../../artifact';
import { IArtifactEditorProps, IArtifactKindConfig } from '../../../../../domain';
import { singleFieldArtifactEditor } from '../singleFieldArtifactEditor';
import { StageConfigField } from '../../../stages/common';
import { SpelText } from '../../../../../widgets';

const TYPE = 'github/file';

export const GithubMatch: IArtifactKindConfig = {
  label: 'GitHub',
  description: 'A file stored in git, hosted by GitHub.',
  key: 'github',
  typePattern: ArtifactTypePatterns.GITHUB_FILE,
  type: TYPE,
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

export const GithubDefault: IArtifactKindConfig = {
  label: 'GitHub',
  typePattern: ArtifactTypePatterns.GITHUB_FILE,
  type: TYPE,
  description: 'A file stored in git, hosted by GitHub.',
  key: 'default.github',
  isDefault: true,
  isMatch: false,
  editCmp: class extends ArtifactEditor {
    constructor(props: IArtifactEditorProps) {
      super(props, TYPE);
    }

    private pathRegex = new RegExp('/repos/[^/]*/[^/]*/contents/(.*)$');

    private onReferenceChange = (reference: string) => {
      const results = this.pathRegex.exec(reference);
      const clonedArtifact = cloneDeep(this.props.artifact);
      if (results !== null) {
        clonedArtifact.name = results[1];
        clonedArtifact.reference = reference;
      } else {
        clonedArtifact.name = reference;
        clonedArtifact.reference = reference;
      }
      this.props.onChange(clonedArtifact);
    };

    public render() {
      return (
        <>
          <StageConfigField label="Content URL" helpKey="pipeline.config.expectedArtifact.defaultGithub.reference">
            <SpelText
              placeholder="https://api.github.com/repos/$ORG/$REPO/contents/$FILEPATH"
              value={this.props.artifact.reference}
              onChange={this.onReferenceChange}
              pipeline={this.props.pipeline}
              docLink={false}
            />
          </StageConfigField>
          <StageConfigField label="Commit/Branch" helpKey="pipeline.config.expectedArtifact.defaultGithub.version">
            <SpelText
              placeholder="master"
              value={this.props.artifact.version}
              onChange={this.onVersionChange}
              pipeline={this.props.pipeline}
              docLink={false}
            />
          </StageConfigField>
        </>
      );
    }
  },
};
