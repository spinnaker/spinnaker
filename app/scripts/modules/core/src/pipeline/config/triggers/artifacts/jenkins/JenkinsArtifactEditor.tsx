import * as React from 'react';

import { cloneDeep } from 'lodash';
import { Option } from 'react-select';

import { IArtifactEditorProps, IArtifactKindConfig, IBuild } from 'core/domain';
import { ArtifactTypePatterns } from 'core/artifact';
import { IgorService } from 'core/ci';
import { StageConfigField } from 'core/pipeline';
import { TetheredSelect } from 'core/presentation';
import { SpelText } from 'core/widgets';

import { singleFieldArtifactEditor } from '../singleFieldArtifactEditor';
import { ArtifactEditor } from '../ArtifactEditor';

const TYPE = 'jenkins/file';

export const JenkinsMatch: IArtifactKindConfig = {
  label: 'Jenkins',
  typePattern: ArtifactTypePatterns.JENKINS_FILE,
  type: TYPE,
  isDefault: false,
  isMatch: true,
  description: 'A Jenkins artifact file.',
  key: 'default.jenkins',
  editCmp: singleFieldArtifactEditor('reference', TYPE, 'Relative path', 'pathFromWorkspaceRoot/file.ext', ''),
};

export const JenkinsDefault: IArtifactKindConfig = {
  label: 'Jenkins',
  typePattern: ArtifactTypePatterns.JENKINS_FILE,
  type: TYPE,
  isDefault: true,
  isMatch: false,
  description: 'A Jenkins artifact file.',
  key: 'default.jenkins',
  editCmp: class extends ArtifactEditor {
    constructor(props: IArtifactEditorProps) {
      super(props, TYPE);
      this.state = {
        jobs: [],
        buildNumbers: [],
      };
    }

    public componentDidMount() {
      IgorService.listJobsForMaster(this.props.account.name).then((jobs: string[]) => {
        this.setState({ jobs });
      });
    }

    private onReferenceChange = (reference: string) => {
      const clonedArtifact = cloneDeep(this.props.artifact);
      clonedArtifact.reference = reference;
      this.props.onChange(clonedArtifact);
    };

    private onNameChange = (name: string) => {
      const clonedArtifact = cloneDeep(this.props.artifact);
      clonedArtifact.name = name;
      this.props.onChange(clonedArtifact);
      IgorService.listBuildsForJob(this.props.account.name, name).then(
        (allBuilds: IBuild[]) => {
          const buildNumbers = allBuilds
            .filter(build => !build.building && build.result === 'SUCCESS')
            .sort((a, b) => b.number - a.number)
            .map(b => b.number);
          this.setState({ buildNumbers });
        },
        () => {
          this.setState({ buildNumbers: [] });
        },
      );
    };

    public render() {
      const { buildNumbers, jobs } = this.state;
      const { artifact } = this.props;
      const buildOptions = buildNumbers.map((b: string) => ({ label: b, value: b }));
      const jobOptions = jobs.map((j: string) => ({ label: j, value: j }));
      return (
        <>
          <StageConfigField label="Job">
            <TetheredSelect
              options={jobOptions}
              onChange={(option: Option) => this.onNameChange(option.value as string)}
              value={artifact.name}
              clearable={false}
            />
          </StageConfigField>
          <StageConfigField label="Build Number">
            <TetheredSelect
              options={buildOptions}
              onChange={(option: Option) => this.onVersionChange(option.value as string)}
              value={artifact.version}
              clearable={false}
            />
          </StageConfigField>
          <StageConfigField label="Relative path">
            <SpelText
              placeholder="pathFromWorkspaceRoot/file.ext"
              value={this.props.artifact.reference}
              onChange={this.onReferenceChange}
              pipeline={this.props.pipeline}
              docLink={false}
            />
          </StageConfigField>
        </>
      );
    }
  },
};
