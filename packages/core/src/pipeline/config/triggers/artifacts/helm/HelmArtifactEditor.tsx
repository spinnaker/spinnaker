import { cloneDeep } from 'lodash';
import React from 'react';
import { Option } from 'react-select';

import { ArtifactService } from '../ArtifactService';
import { ArtifactTypePatterns } from '../../../../../artifact';
import { IArtifact, IArtifactEditorProps, IArtifactKindConfig } from '../../../../../domain';
import { TetheredCreatable, TetheredSelect } from '../../../../../presentation';
import { StageConfigField } from '../../../stages/common';
import { Spinner } from '../../../../../widgets';

const TYPE = 'helm/chart';

interface IHelmArtifactEditorState {
  names: string[];
  versions: Array<Option<string>>;
  versionsLoading: boolean;
  namesLoading: boolean;
}

class HelmEditor extends React.Component<IArtifactEditorProps, IHelmArtifactEditorState> {
  public state: IHelmArtifactEditorState = {
    names: [],
    versions: [],
    versionsLoading: true,
    namesLoading: true,
  };

  // taken from https://github.com/semver/semver/issues/232
  private SEMVER = new RegExp(
    /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(-(0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*)(\.(0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*))*)?(\+[0-9a-zA-Z-]+(\.[0-9a-zA-Z-]+)*)?$/,
  );

  constructor(props: IArtifactEditorProps) {
    super(props);
    const { artifact } = this.props;
    if (artifact.type !== TYPE) {
      const clonedArtifact = cloneDeep(artifact);
      clonedArtifact.type = TYPE;
      props.onChange(clonedArtifact);
    }

    ArtifactService.getArtifactNames(TYPE, this.props.account.name).then(
      (names) => {
        this.setState({
          names,
          namesLoading: false,
        });
      },
      () => {
        this.setState({
          names: [],
          namesLoading: false,
          versionsLoading: false,
          versions: [],
        });
      },
    );
  }

  public componentDidMount() {
    const { artifact } = this.props;
    if (artifact.name) {
      this.getChartVersionOptions(artifact.name);
    }
  }

  public componentDidUpdate(prevProps: IArtifactEditorProps) {
    if (this.props.account.name !== prevProps.account.name) {
      ArtifactService.getArtifactNames(TYPE, this.props.account.name).then(
        (names) => {
          this.setState({
            names,
            namesLoading: false,
            versions: [],
          });
        },
        () => {
          this.setState({
            names: [],
            namesLoading: false,
            versions: [],
          });
        },
      );
    }
  }

  public render() {
    const { artifact } = this.props;
    const nameOptions = this.state.names.map((name) => ({ value: name, label: name }));

    return (
      <>
        <StageConfigField label="Name">
          {!this.state.namesLoading && (
            <TetheredSelect
              options={nameOptions}
              value={artifact.name || ''}
              onChange={(e: Option) => {
                this.onChange(e, 'name');
                this.getChartVersionOptions(e.value.toString());
              }}
              clearable={false}
            />
          )}
          {this.state.namesLoading && <Spinner />}
        </StageConfigField>
        <StageConfigField label="Version">
          {!this.state.versionsLoading && (
            <TetheredCreatable
              options={this.state.versions}
              value={artifact.version || ''}
              onChange={(e: Option) => {
                this.onChange(e, 'version');
              }}
              clearable={false}
            />
          )}
          {this.state.versionsLoading && <Spinner />}
        </StageConfigField>
      </>
    );
  }

  private onChange = (e: Option, field: keyof IArtifact) => {
    const clone = cloneDeep(this.props.artifact);
    (clone[field] as any) = e.value.toString();
    this.props.onChange(clone);
  };

  private getChartVersionOptions(chartName: string) {
    const { artifact, account } = this.props;
    this.setState({ versionsLoading: true });
    ArtifactService.getArtifactVersions(TYPE, account.name, chartName).then((versions: string[]) => {
      // if the version doesn't match SEMVER we assume that it's a regular expression or SpEL expression
      // and add it to the list of valid versions
      if (artifact.version && !this.SEMVER.test(artifact.version)) {
        versions = versions.concat(artifact.version);
      }
      this.setState({
        versions: versions.map((v) => ({ label: v, value: v })),
        versionsLoading: false,
      });
    });
  }
}

export const HelmMatch: IArtifactKindConfig = {
  label: 'Helm',
  typePattern: ArtifactTypePatterns.HELM_CHART,
  type: TYPE,
  isDefault: false,
  isMatch: true,
  description: 'A helm chart to be deployed',
  key: 'helm',
  editCmp: HelmEditor,
};

export const HelmDefault: IArtifactKindConfig = {
  label: 'Helm',
  typePattern: ArtifactTypePatterns.HELM_CHART,
  type: TYPE,
  isDefault: true,
  isMatch: false,
  description: 'A helm chart to be deployed',
  key: 'default.helm',
  editCmp: HelmEditor,
};
