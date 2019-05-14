import * as React from 'react';
import Select, { Option } from 'react-select';

import { Observable, Subject } from 'rxjs';

import { IArtifactoryTrigger } from 'core/domain/ITrigger';
import { BaseTrigger } from 'core/pipeline';
import { ArtifactoryReaderService } from './artifactoryReader.service';
import { Application } from '@spinnaker/core';

export interface IArtifactoryTriggerConfigProps {
  trigger: IArtifactoryTrigger;
  pipelineId: string;
  application: Application;
  triggerUpdated: (trigger: IArtifactoryTrigger) => void;
}

export interface IArtifactoryTriggerConfigState {
  artifactorySearchNames: string[];
}

export class ArtifactoryTrigger extends React.Component<
  IArtifactoryTriggerConfigProps,
  IArtifactoryTriggerConfigState
> {
  private destroy$ = new Subject();

  constructor(props: IArtifactoryTriggerConfigProps) {
    super(props);
    this.state = {
      artifactorySearchNames: [],
    };
  }

  public componentDidMount() {
    Observable.fromPromise(ArtifactoryReaderService.getArtifactoryNames())
      .takeUntil(this.destroy$)
      .subscribe((artifactorySearchNames: string[]) => {
        this.setState({ artifactorySearchNames });
      });
  }

  public componentWillUnmount(): void {
    this.destroy$.next();
  }

  private onUpdateTrigger = (update: any) => {
    this.props.triggerUpdated &&
      this.props.triggerUpdated({
        ...this.props.trigger,
        ...update,
      });
  };

  private ArtifactoryTriggerContents = () => {
    const { artifactorySearchNames } = this.state;
    const { artifactorySearchName } = this.props.trigger;
    return (
      <>
        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            <span className="label-text">Artifactory Name</span>
          </div>
          <div className="col-md-6">
            <Select
              value={artifactorySearchName}
              placeholder="Select Artifactory search name..."
              onChange={(option: Option<string>) => this.onUpdateTrigger({ artifactorySearchName: option.value })}
              options={artifactorySearchNames.map((name: string) => ({ label: name, value: name }))}
              clearable={false}
            />
          </div>
        </div>
      </>
    );
  };

  public render() {
    const { ArtifactoryTriggerContents } = this;
    return <BaseTrigger {...this.props} triggerContents={<ArtifactoryTriggerContents />} />;
  }
}
