import * as React from 'react';
import { Option } from 'react-select';
import { isFinite } from 'lodash';

import { IArtifact, IArtifactEditorProps } from 'core/domain';
import { TetheredSelect } from 'core/presentation';
import { ArtifactService } from 'core/pipeline/config/triggers/artifacts/ArtifactService';

interface IState {
  names: string[];
  versions: string[];
  artifact: IArtifact;
}

export class HelmArtifactEditor extends React.Component<IArtifactEditorProps, IState> {
  constructor(props: IArtifactEditorProps) {
    super(props);
    this.state = { names: [], versions: [], artifact: this.props.artifact };
    this.state.artifact.artifactAccount = this.props.account.name;
    this.state.artifact.reference = this.props.account.name;
    ArtifactService.getArtifactNames('helm/chart', this.props.account.name).then(names => {
      this.setState({ names });
    });
  }

  public componentWillReceiveProps(nextProps: IArtifactEditorProps) {
    if (this.props.account.name !== nextProps.account.name) {
      this.state.artifact.artifactAccount = nextProps.account.name;
      this.state.artifact.name = '';
      this.state.artifact.version = '';
      this.state.artifact.reference = nextProps.account.name;
      nextProps.onChange({ ...this.state.artifact });
      ArtifactService.getArtifactNames('helm/chart', nextProps.account.name).then(names => {
        this.setState({ names, versions: [] });
      });
    }
  }

  public render() {
    const nameOptions = this.state.names.map(name => ({ value: name, label: name }));
    const versionOptions = this.state.versions.map(version => ({ value: version, label: version }));
    const labelColumns = isFinite(this.props.labelColumns) ? this.props.labelColumns : 2;
    const labelClassName = 'col-md-' + labelColumns;
    return (
      <div className="form-group row">
        <label className={labelClassName + ' sm-label-right'}>Name</label>
        <TetheredSelect
          className={'col-md-3'}
          options={nameOptions}
          value={this.state.artifact.name || ''}
          onChange={(e: Option) => {
            this.onChange(e, 'name');
            this.onNameChange();
          }}
          clearable={false}
        />
        <label className={'col-md-2 sm-label-right'}>Version</label>
        <TetheredSelect
          className={'col-md-3'}
          options={versionOptions}
          value={this.state.artifact.version || ''}
          onChange={(e: Option) => {
            this.onChange(e, 'version');
          }}
          clearable={false}
        />
      </div>
    );
  }

  private onChange = (e: Option, field: keyof IArtifact) => {
    this.state.artifact[field] = e.value.toString();
    this.props.onChange({ ...this.state.artifact });
  };

  private onNameChange = () => {
    ArtifactService.getArtifactVersions(
      'helm/chart',
      this.state.artifact.artifactAccount,
      this.state.artifact.name,
    ).then(versions => {
      this.setState({ versions });
    });
  };
}
