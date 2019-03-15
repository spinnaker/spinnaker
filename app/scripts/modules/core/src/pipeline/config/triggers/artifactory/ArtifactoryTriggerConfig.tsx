import * as React from 'react';
import Select, { Option } from 'react-select';

import { IArtifactoryTrigger } from 'core/domain/ITrigger';
import { ArtifactoryReaderService } from './artifactoryReader.service';
import { ITriggerConfigProps } from '../ITriggerConfigProps';

export interface IArtifactoryTriggerConfigProps extends ITriggerConfigProps {
  trigger: IArtifactoryTrigger;
}

export interface IArtifactoryTriggerConfigState {
  artifactorySearchNames: string[];
}

export class ArtifactoryTriggerConfig extends React.Component<
  IArtifactoryTriggerConfigProps,
  IArtifactoryTriggerConfigState
> {
  constructor(props: IArtifactoryTriggerConfigProps) {
    super(props);
    this.state = {
      artifactorySearchNames: [],
    };
  }

  public componentDidMount() {
    ArtifactoryReaderService.getArtifactoryNames().then((names: string[]) => {
      this.setState({
        artifactorySearchNames: names,
      });
    });
  }

  private onArtifactorySearchNameChanged = (option: Option<string>) => {
    Object.assign(this.props.trigger, { artifactorySearchName: option.value });
    this.props.fieldUpdated();
  };

  public render() {
    const { artifactorySearchNames } = this.state;
    const { artifactorySearchName } = this.props.trigger;
    return (
      <div className="sp-formItem">
        <div className="sp-formItem__left">
          <div className="sp-formLabel">Artifactory Name</div>
        </div>
        <div className="sp-formItem__right">
          <div className="sp-form">
            <span className="field">
              <Select
                value={artifactorySearchName}
                placeholder="Select Artifactory search name..."
                onChange={this.onArtifactorySearchNameChanged}
                options={artifactorySearchNames.map((name: string) => ({ label: name, value: name }))}
                clearable={false}
              />
            </span>
          </div>
        </div>
      </div>
    );
  }
}
