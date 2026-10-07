import { cloneDeep } from 'lodash';
import React from 'react';

import { ArtifactEditor } from '../ArtifactEditor';
import { ArtifactTypePatterns } from '../../../../../artifact/ArtifactTypes';
import type { IArtifactEditorProps, IArtifactKindConfig } from '../../../../../domain';
import { singleFieldArtifactEditor } from '../singleFieldArtifactEditor';
import { StageConfigField } from '../../../stages/common';
import { SpelText } from '../../../../../widgets/spelText/SpelText';

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

type BitbucketFlavor = 'server' | 'cloud';

const DEFAULT_SERVER_BASE_URL = 'https://bitbucket.mycompany.com/rest/api/1.0';
const DEFAULT_CLOUD_BASE_URL = 'https://api.bitbucket.org/1.0';

// Bitbucket Server (Stash): {baseUrl}/projects/$PROJECTKEY/repos/$REPONAME/raw/$FILEPATH
const serverUrlRegex = new RegExp('^(.+?)/projects/([^/]+)/repos/([^/]+)/raw/(.+)$');
// Bitbucket Cloud: {baseUrl}/repositories/$ORG/$REPO/raw/$VERSION/$FILEPATH
const cloudUrlRegex = new RegExp('^(.+?)/repositories/([^/]+)/([^/]+)/raw/([^/]+)/(.+)$');

interface IBitbucketBuilderState {
  useUrlBuilder: boolean;
  flavor: BitbucketFlavor;
  baseUrl: string;
  projectKey: string;
  repoName: string;
  org: string;
  repo: string;
  filePath: string;
}

export const BitbucketDefault: IArtifactKindConfig = {
  label: 'Bitbucket',
  typePattern: ArtifactTypePatterns.BITBUCKET_FILE,
  type: TYPE,
  description: 'A file stored in git, hosted by Bitbucket.',
  key: 'default.bitbucket',
  isDefault: true,
  isMatch: false,
  editCmp: class extends ArtifactEditor {
    public state: IBitbucketBuilderState;

    constructor(props: IArtifactEditorProps) {
      super(props, TYPE);
      const reference = props.artifact.reference || '';
      const serverResults = serverUrlRegex.exec(reference);
      const cloudResults = !serverResults ? cloudUrlRegex.exec(reference) : null;

      this.state = {
        useUrlBuilder: !reference || serverResults !== null || cloudResults !== null,
        flavor: cloudResults ? 'cloud' : 'server',
        baseUrl: serverResults ? serverResults[1] : cloudResults ? cloudResults[1] : DEFAULT_SERVER_BASE_URL,
        projectKey: serverResults ? serverResults[2] : '',
        repoName: serverResults ? serverResults[3] : '',
        org: cloudResults ? cloudResults[2] : '',
        repo: cloudResults ? cloudResults[3] : '',
        filePath: serverResults ? serverResults[4] : cloudResults ? cloudResults[5] : props.artifact.name || '',
      };
      if (cloudResults) {
        this.onVersionChange(cloudResults[4]);
      }
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

    private buildReference = (state: IBitbucketBuilderState, version: string): string => {
      const { flavor, baseUrl, projectKey, repoName, org, repo, filePath } = state;
      const base = baseUrl ? baseUrl.replace(/\/$/, '') : '';
      if (flavor === 'server') {
        if (!base || !projectKey || !repoName || !filePath) {
          return '';
        }
        return `${base}/projects/${projectKey}/repos/${repoName}/raw/${filePath}`;
      }
      if (!base || !org || !repo || !version || !filePath) {
        return '';
      }
      return `${base}/repositories/${org}/${repo}/raw/${version}/${filePath}`;
    };

    private updateBuilderField = (
      field: 'baseUrl' | 'projectKey' | 'repoName' | 'org' | 'repo' | 'filePath',
      value: string,
    ) => {
      const nextBuilderState = { ...this.state, [field]: value };
      this.setState(nextBuilderState);

      const clonedArtifact = cloneDeep(this.props.artifact);
      clonedArtifact.reference = this.buildReference(nextBuilderState, this.props.artifact.version);
      clonedArtifact.name = nextBuilderState.filePath;
      this.props.onChange(clonedArtifact);
    };

    private onBuilderVersionChange = (version: string) => {
      const clonedArtifact = cloneDeep(this.props.artifact);
      clonedArtifact.version = version;
      clonedArtifact.reference = this.buildReference(this.state, version);
      clonedArtifact.name = this.state.filePath;
      this.props.onChange(clonedArtifact);
    };

    private setFlavor = (flavor: BitbucketFlavor) => {
      const nextBuilderState = {
        ...this.state,
        flavor,
        baseUrl: flavor === 'cloud' ? DEFAULT_CLOUD_BASE_URL : DEFAULT_SERVER_BASE_URL,
      };
      this.setState(nextBuilderState);

      const clonedArtifact = cloneDeep(this.props.artifact);
      clonedArtifact.reference = this.buildReference(nextBuilderState, this.props.artifact.version);
      clonedArtifact.name = nextBuilderState.filePath;
      this.props.onChange(clonedArtifact);
    };

    private toggleUrlBuilder = () => {
      this.setState({ useUrlBuilder: !this.state.useUrlBuilder });
    };

    public render() {
      const { useUrlBuilder, flavor, baseUrl, projectKey, repoName, org, repo, filePath } = this.state;
      return (
        <>
          <div className="checkbox">
            <label>
              <input type="checkbox" checked={useUrlBuilder} onChange={this.toggleUrlBuilder} />
              <span> Build the Object path from an API base URL, repo details, and file path</span>
            </label>
          </div>
          {useUrlBuilder ? (
            <>
              <div className="radio">
                <label>
                  <input type="radio" checked={flavor === 'server'} onChange={() => this.setFlavor('server')} />
                  <span> Bitbucket Server</span>
                </label>
              </div>
              <div className="radio">
                <label>
                  <input type="radio" checked={flavor === 'cloud'} onChange={() => this.setFlavor('cloud')} />
                  <span> Bitbucket Cloud</span>
                </label>
              </div>
              <StageConfigField
                label="API Base URL"
                helpKey="pipeline.config.expectedArtifact.defaultBitbucket.baseUrl"
              >
                <SpelText
                  placeholder={flavor === 'cloud' ? DEFAULT_CLOUD_BASE_URL : DEFAULT_SERVER_BASE_URL}
                  value={baseUrl}
                  onChange={(value) => this.updateBuilderField('baseUrl', value)}
                  pipeline={this.props.pipeline}
                  docLink={false}
                />
              </StageConfigField>
              {flavor === 'server' ? (
                <>
                  <StageConfigField
                    label="Project Key"
                    helpKey="pipeline.config.expectedArtifact.defaultBitbucket.projectKey"
                  >
                    <SpelText
                      placeholder="$PROJECTKEY"
                      value={projectKey}
                      onChange={(value) => this.updateBuilderField('projectKey', value)}
                      pipeline={this.props.pipeline}
                      docLink={false}
                    />
                  </StageConfigField>
                  <StageConfigField
                    label="Repo Name"
                    helpKey="pipeline.config.expectedArtifact.defaultBitbucket.repoName"
                  >
                    <SpelText
                      placeholder="$REPONAME"
                      value={repoName}
                      onChange={(value) => this.updateBuilderField('repoName', value)}
                      pipeline={this.props.pipeline}
                      docLink={false}
                    />
                  </StageConfigField>
                </>
              ) : (
                <>
                  <StageConfigField label="Org" helpKey="pipeline.config.expectedArtifact.defaultBitbucket.org">
                    <SpelText
                      placeholder="$ORG"
                      value={org}
                      onChange={(value) => this.updateBuilderField('org', value)}
                      pipeline={this.props.pipeline}
                      docLink={false}
                    />
                  </StageConfigField>
                  <StageConfigField label="Repo" helpKey="pipeline.config.expectedArtifact.defaultBitbucket.repo">
                    <SpelText
                      placeholder="$REPO"
                      value={repo}
                      onChange={(value) => this.updateBuilderField('repo', value)}
                      pipeline={this.props.pipeline}
                      docLink={false}
                    />
                  </StageConfigField>
                  <StageConfigField
                    label="Commit/Branch"
                    helpKey="pipeline.config.expectedArtifact.defaultBitbucket.version"
                  >
                    <SpelText
                      placeholder="master"
                      value={this.props.artifact.version}
                      onChange={this.onBuilderVersionChange}
                      pipeline={this.props.pipeline}
                      docLink={false}
                    />
                  </StageConfigField>
                </>
              )}
              <StageConfigField label="File Path" helpKey="pipeline.config.expectedArtifact.defaultBitbucket.filepath">
                <SpelText
                  placeholder="path/to/file.yml"
                  value={filePath}
                  onChange={(value) => this.updateBuilderField('filePath', value)}
                  pipeline={this.props.pipeline}
                  docLink={false}
                />
              </StageConfigField>
              <StageConfigField label="Object path">
                <span className="content-url-preview">
                  {this.buildReference(this.state, this.props.artifact.version) || '(fill in the fields above)'}
                </span>
              </StageConfigField>
            </>
          ) : (
            <>
              <StageConfigField
                label="Object path"
                helpKey="pipeline.config.expectedArtifact.defaultBitbucket.reference"
              >
                <SpelText
                  placeholder="https://api.bitbucket.org/1.0/repositories/$ORG/$REPO/raw/$VERSION/$FILEPATH"
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
          )}
        </>
      );
    }
  },
};
