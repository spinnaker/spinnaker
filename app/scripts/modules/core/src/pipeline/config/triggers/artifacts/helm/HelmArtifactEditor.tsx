import * as React from 'react';
import { Option } from 'react-select';

import { ArtifactTypePatterns } from 'core/artifact';
import { IArtifact, IArtifactEditorProps, IArtifactKindConfig } from 'core/domain';
import { StageConfigField } from 'core/pipeline';
import { TetheredSelect } from 'core/presentation';

import { ArtifactService } from '../ArtifactService';
import { cloneDeep } from 'lodash';

const TYPE = 'helm/chart';

interface IHelmArtifactEditorState {
  names: string[];
  versions: string[];
}

class HelmEditor extends React.Component<IArtifactEditorProps, IHelmArtifactEditorState> {
  constructor(props: IArtifactEditorProps) {
    super(props);
    if (props.artifact.type !== TYPE) {
      const clonedArtifact = cloneDeep(props.artifact);
      clonedArtifact.type = TYPE;
      props.onChange(clonedArtifact);
    }
    ArtifactService.getArtifactNames(TYPE, this.props.account.name).then(names => {
      this.setState({ names });
    });
  }

  public componentWillReceiveProps(nextProps: IArtifactEditorProps) {
    if (this.props.account.name !== nextProps.account.name) {
      ArtifactService.getArtifactNames(TYPE, nextProps.account.name).then(names => {
        this.setState({ names, versions: [] });
      });
    }
  }

  public render() {
    const { artifact } = this.props;
    const nameOptions = this.state.names.map(name => ({ value: name, label: name }));
    const versionOptions = this.state.versions.map(version => ({ value: version, label: version }));
    return (
      <>
        <StageConfigField label="Name">
          <TetheredSelect
            className={'col-md-3'}
            options={nameOptions}
            value={artifact.name || ''}
            onChange={(e: Option) => {
              this.onChange(e, 'name');
              this.onNameChange();
            }}
            clearable={false}
          />
        </StageConfigField>
        <StageConfigField label="Version">
          <TetheredSelect
            className={'col-md-3'}
            options={versionOptions}
            value={artifact.version || ''}
            onChange={(e: Option) => {
              this.onChange(e, 'version');
            }}
            clearable={false}
          />
        </StageConfigField>
      </>
    );
  }

  private onChange = (e: Option, field: keyof IArtifact) => {
    const clone = cloneDeep(this.props.artifact);
    clone[field] = e.value.toString();
    this.props.onChange(clone);
  };

  private onNameChange = () => {
    ArtifactService.getArtifactVersions(TYPE, this.props.artifact.artifactAccount, this.props.artifact.name).then(
      versions => {
        this.setState({ versions });
      },
    );
  };
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
