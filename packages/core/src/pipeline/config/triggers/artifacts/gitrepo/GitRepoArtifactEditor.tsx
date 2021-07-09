import { cloneDeep } from 'lodash';
import React from 'react';

import { ArtifactTypePatterns } from '../../../../../artifact';
import { IArtifactEditorProps, IArtifactKindConfig } from '../../../../../domain';
import { CheckboxInput } from '../../../../../presentation';
import { StageConfigField } from '../../../stages/common';
import { SpelText } from '../../../../../widgets';

const TYPE = 'git/repo';

interface IGitRepoArtifactEditorState {
  includesSubPath: boolean;
}

class GitRepoArtifactEditor extends React.Component<IArtifactEditorProps, IGitRepoArtifactEditorState> {
  public constructor(props: IArtifactEditorProps) {
    super(props);
    const { artifact } = this.props;
    this.state = {
      includesSubPath: (artifact.location ?? artifact.metadata?.subPath) !== undefined,
    };
  }

  private onIncludesSubPathChange = (event: any) => {
    this.setState({ includesSubPath: event.target.checked });
  };

  private onReferenceChanged = (reference: string) => {
    const clonedArtifact = cloneDeep(this.props.artifact);
    clonedArtifact.reference = reference;
    this.props.onChange(clonedArtifact);
  };

  private onVersionChanged = (version: string) => {
    const clonedArtifact = cloneDeep(this.props.artifact);
    clonedArtifact.version = version;
    this.props.onChange(clonedArtifact);
  };

  private onSubPathChanged = (subPath: string) => {
    const clonedArtifact = cloneDeep(this.props.artifact);
    clonedArtifact.location = subPath;
    this.props.onChange(clonedArtifact);
  };

  public render() {
    const { artifact } = this.props;

    return (
      <>
        <StageConfigField label="URL" helpKey="pipeline.config.expectedArtifact.gitrepo.url">
          <SpelText
            placeholder="https or ssh to your git repo"
            value={artifact.reference}
            onChange={this.onReferenceChanged}
            pipeline={this.props.pipeline}
            docLink={true}
          />
        </StageConfigField>
        <StageConfigField label="Branch" helpKey="pipeline.config.expectedArtifact.gitrepo.branch">
          <SpelText
            placeholder="master"
            value={artifact.version}
            onChange={this.onVersionChanged}
            pipeline={this.props.pipeline}
            docLink={true}
          />
        </StageConfigField>
        <StageConfigField label="Checkout subpath" helpKey="pipeline.config.expectedArtifact.gitrepo.checkoutSubpath">
          <CheckboxInput checked={this.state.includesSubPath} onChange={this.onIncludesSubPathChange} />
        </StageConfigField>
        {this.state.includesSubPath && (
          <StageConfigField label="Subpath" helpKey="pipeline.config.expectedArtifact.gitrepo.subpath">
            <SpelText
              placeholder="path/to/subdirectory"
              value={artifact.location ?? artifact.metadata?.subPath}
              onChange={this.onSubPathChanged}
              pipeline={this.props.pipeline}
              docLink={true}
            />
          </StageConfigField>
        )}
      </>
    );
  }
}

export const GitRepoMatch: IArtifactKindConfig = {
  label: 'GitRepo',
  description: 'A Git repository.',
  key: 'gitrepo',
  typePattern: ArtifactTypePatterns.GIT_REPO,
  type: TYPE,
  isDefault: false,
  isMatch: true,
  editCmp: GitRepoArtifactEditor,
};

export const GitRepoDefault: IArtifactKindConfig = {
  label: 'GitRepo',
  typePattern: ArtifactTypePatterns.GIT_REPO,
  type: TYPE,
  description: 'A Git repository.',
  key: 'default.gitrepo',
  isDefault: true,
  isMatch: false,
  editCmp: GitRepoArtifactEditor,
};
