import { cloneDeep } from 'lodash';
import React from 'react';

import { ArtifactEditor } from '../ArtifactEditor';
import { ArtifactTypePatterns } from '../../../../../artifact/ArtifactTypes';
import type { IArtifactEditorProps, IArtifactKindConfig } from '../../../../../domain';
import { singleFieldArtifactEditor } from '../singleFieldArtifactEditor';
import { StageConfigField } from '../../../stages/common';
import { SpelText } from '../../../../../widgets/spelText/SpelText';

const TYPE = 'gitea/file';

export const GiteaMatch: IArtifactKindConfig = {
  label: 'Gitea',
  typePattern: ArtifactTypePatterns.GITEA_FILE,
  type: TYPE,
  description: 'A file stored in git, hosted by Gitea.',
  key: 'gitea',
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

const DEFAULT_API_BASE_URL = 'https://gitea.example.com/api/v1';

// Captures the API base URL (everything before /repos/), the org, the repo, and the file path from a
// raw content URL such as https://gitea.example.com/api/v1/repos/$ORG/$REPO/raw/path/to/file.yml.
const contentUrlRegex = new RegExp('^(.+?)/repos/([^/]+)/([^/]+)/raw/(.+?)(?:\\?.*)?$');

interface IGiteaBuilderState {
  useUrlBuilder: boolean;
  baseUrl: string;
  org: string;
  repo: string;
  filePath: string;
}

const encodeFilePath = (filePath: string): string => filePath.split('/').map(encodeURIComponent).join('/');

const decodeFilePath = (filePath: string): string => filePath.split('/').map(decodeURIComponent).join('/');

export const GiteaDefault: IArtifactKindConfig = {
  label: 'Gitea',
  typePattern: ArtifactTypePatterns.GITEA_FILE,
  type: TYPE,
  description: 'A file stored in git, hosted by Gitea.',
  key: 'default.gitea',
  isDefault: true,
  isMatch: false,
  editCmp: class extends ArtifactEditor {
    public state: IGiteaBuilderState;

    constructor(props: IArtifactEditorProps) {
      super(props, TYPE);
      const results = contentUrlRegex.exec(props.artifact.reference || '');
      this.state = {
        useUrlBuilder: !props.artifact.reference || results !== null,
        baseUrl: results ? results[1] : DEFAULT_API_BASE_URL,
        org: results ? decodeURIComponent(results[2]) : '',
        repo: results ? decodeURIComponent(results[3]) : '',
        filePath: results ? decodeFilePath(results[4]) : props.artifact.name || '',
      };
    }

    private onReferenceChange = (reference: string) => {
      const results = contentUrlRegex.exec(reference);
      const clonedArtifact = cloneDeep(this.props.artifact);
      clonedArtifact.name = results !== null ? decodeFilePath(results[4]) : reference;
      clonedArtifact.reference = reference;
      this.props.onChange(clonedArtifact);
    };

    private buildReference = (baseUrl: string, org: string, repo: string, filePath: string): string => {
      if (!baseUrl || !org || !repo || !filePath) {
        return '';
      }
      return `${baseUrl.replace(/\/$/, '')}/repos/${encodeURIComponent(org)}/${encodeURIComponent(
        repo,
      )}/raw/${encodeFilePath(filePath)}`;
    };

    private updateBuilderField = (field: 'baseUrl' | 'org' | 'repo' | 'filePath', value: string) => {
      const nextBuilderState = { ...this.state, [field]: value };
      this.setState(nextBuilderState);

      const clonedArtifact = cloneDeep(this.props.artifact);
      clonedArtifact.reference = this.buildReference(
        nextBuilderState.baseUrl,
        nextBuilderState.org,
        nextBuilderState.repo,
        nextBuilderState.filePath,
      );
      clonedArtifact.name = nextBuilderState.filePath;
      this.props.onChange(clonedArtifact);
    };

    private toggleUrlBuilder = () => {
      this.setState({ useUrlBuilder: !this.state.useUrlBuilder });
    };

    public render() {
      const { useUrlBuilder, baseUrl, org, repo, filePath } = this.state;
      return (
        <>
          <div className="checkbox">
            <label>
              <input type="checkbox" checked={useUrlBuilder} onChange={this.toggleUrlBuilder} />
              <span> Build the Content URL from an API base URL, org, repo, and file path</span>
            </label>
          </div>
          {useUrlBuilder ? (
            <>
              <StageConfigField label="API Base URL" helpKey="pipeline.config.expectedArtifact.defaultGitea.baseUrl">
                <SpelText
                  placeholder={DEFAULT_API_BASE_URL}
                  value={baseUrl}
                  onChange={(value) => this.updateBuilderField('baseUrl', value)}
                  pipeline={this.props.pipeline}
                  docLink={false}
                />
              </StageConfigField>
              <StageConfigField label="Org" helpKey="pipeline.config.expectedArtifact.defaultGitea.org">
                <SpelText
                  placeholder="$ORG"
                  value={org}
                  onChange={(value) => this.updateBuilderField('org', value)}
                  pipeline={this.props.pipeline}
                  docLink={false}
                />
              </StageConfigField>
              <StageConfigField label="Repo" helpKey="pipeline.config.expectedArtifact.defaultGitea.repo">
                <SpelText
                  placeholder="$REPO"
                  value={repo}
                  onChange={(value) => this.updateBuilderField('repo', value)}
                  pipeline={this.props.pipeline}
                  docLink={false}
                />
              </StageConfigField>
              <StageConfigField label="File Path" helpKey="pipeline.config.expectedArtifact.git.name">
                <SpelText
                  placeholder="manifests/frontend.yaml"
                  value={filePath}
                  onChange={(value) => this.updateBuilderField('filePath', value)}
                  pipeline={this.props.pipeline}
                  docLink={false}
                />
              </StageConfigField>
              <StageConfigField label="Content URL">
                <span className="content-url-preview">
                  {this.buildReference(baseUrl, org, repo, filePath) || '(fill in the fields above)'}
                </span>
              </StageConfigField>
            </>
          ) : (
            <StageConfigField label="Content URL" helpKey="pipeline.config.expectedArtifact.defaultGitea.reference">
              <SpelText
                placeholder="https://gitea.example.com/api/v1/repos/$ORG/$REPO/raw/path/to/file.yml"
                value={this.props.artifact.reference}
                onChange={this.onReferenceChange}
                pipeline={this.props.pipeline}
                docLink={false}
              />
            </StageConfigField>
          )}
          <StageConfigField label="Commit/Branch" helpKey="pipeline.config.expectedArtifact.defaultGitea.version">
            <SpelText
              placeholder="default branch"
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
