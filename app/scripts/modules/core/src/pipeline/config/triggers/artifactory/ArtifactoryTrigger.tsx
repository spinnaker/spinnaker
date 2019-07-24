import * as React from 'react';

import { Observable, Subject } from 'rxjs';

import { IArtifactoryTrigger } from 'core/domain/ITrigger';
import { BaseTrigger } from 'core/pipeline';
import { FormField, ReactSelectInput } from 'core/presentation';
import { Application } from 'core/application';

import { ArtifactoryReaderService } from './artifactoryReader.service';

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

  public componentWillUnmount() {
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
      <FormField
        label="Artifactory Name"
        value={artifactorySearchName}
        onChange={e => this.onUpdateTrigger({ artifactorySearchName: e.target.value })}
        input={props => (
          <ReactSelectInput
            {...props}
            placeholder="Select Artifactory search name..."
            stringOptions={artifactorySearchNames}
            clearable={false}
          />
        )}
      />
    );
  };

  public render() {
    const { ArtifactoryTriggerContents } = this;
    return <BaseTrigger {...this.props} triggerContents={<ArtifactoryTriggerContents />} />;
  }
}
