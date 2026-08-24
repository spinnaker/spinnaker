import { cloneDeep } from 'lodash';
import React from 'react';

import { ArtifactEditor } from '../ArtifactEditor';
import { ArtifactTypePatterns } from '../../../../../artifact/ArtifactTypes';
import type { IArtifactEditorProps, IArtifactKindConfig } from '../../../../../domain';
import { singleFieldArtifactEditor } from '../singleFieldArtifactEditor';
import { StageConfigField } from '../../../stages/common';
import { SpelText } from '../../../../../widgets/spelText/SpelText';

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

const DEFAULT_API_BASE_URL = 'https://api.github.com';

// Captures the API base URL (everything before /repos/), org, repo, and file path from a content URL,
// so both github.com and GitHub Enterprise URLs (which use a custom domain/path prefix) can be parsed.
const contentUrlRegex = new RegExp('^(.+?)/repos/([^/]+)/([^/]+)/contents/(.+)$');

interface IGithubBuilderState {
  useUrlBuilder: boolean;
  baseUrl: string;
  org: string;
  repo: string;
  filePath: string;
}

export const GithubDefault: IArtifactKindConfig = {
  label: 'GitHub',
  typePattern: ArtifactTypePatterns.GITHUB_FILE,
  type: TYPE,
  description: 'A file stored in git, hosted by GitHub.',
  key: 'default.github',
  isDefault: true,
  isMatch: false,
  editCmp: class extends ArtifactEditor {
    public state: IGithubBuilderState;

    constructor(props: IArtifactEditorProps) {
      super(props, TYPE);
      const results = contentUrlRegex.exec(props.artifact.reference || '');
      this.state = {
        useUrlBuilder: !props.artifact.reference || results !== null,
        baseUrl: results ? results[1] : DEFAULT_API_BASE_URL,
        org: results ? results[2] : '',
        repo: results ? results[3] : '',
        filePath: results ? results[4] : props.artifact.name || '',
      };
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

    private buildReference = (baseUrl: string, org: string, repo: string, filePath: string): string => {
      if (!baseUrl || !org || !repo || !filePath) {
        return '';
      }
      return `${baseUrl.replace(/\/$/, '')}/repos/${org}/${repo}/contents/${filePath}`;
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
              <StageConfigField label="API Base URL" helpKey="pipeline.config.expectedArtifact.defaultGithub.baseUrl">
                <SpelText
                  placeholder={DEFAULT_API_BASE_URL}
                  value={baseUrl}
                  onChange={(value) => this.updateBuilderField('baseUrl', value)}
                  pipeline={this.props.pipeline}
                  docLink={false}
                />
              </StageConfigField>
              <StageConfigField label="Org" helpKey="pipeline.config.expectedArtifact.defaultGithub.org">
                <SpelText
                  placeholder="$ORG"
                  value={org}
                  onChange={(value) => this.updateBuilderField('org', value)}
                  pipeline={this.props.pipeline}
                  docLink={false}
                />
              </StageConfigField>
              <StageConfigField label="Repo" helpKey="pipeline.config.expectedArtifact.defaultGithub.repo">
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
            <StageConfigField label="Content URL" helpKey="pipeline.config.expectedArtifact.defaultGithub.reference">
              <SpelText
                placeholder="https://api.github.com/repos/$ORG/$REPO/contents/$FILEPATH"
                value={this.props.artifact.reference}
                onChange={this.onReferenceChange}
                pipeline={this.props.pipeline}
                docLink={false}
              />
            </StageConfigField>
          )}
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
