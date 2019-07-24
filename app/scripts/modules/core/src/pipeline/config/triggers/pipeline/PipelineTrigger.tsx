import * as React from 'react';
import { Observable, Subject } from 'rxjs';

import { Application, ApplicationReader } from 'core/application';
import { BaseTrigger, PipelineConfigService } from 'core/pipeline';
import { IPipeline, IPipelineTrigger } from 'core/domain';
import { ChecklistInput, FormField, Omit, ReactSelectInput } from 'core/presentation';

type IPipelineTriggerConfig = Omit<IPipelineTrigger, 'parentExecution' | 'parentPipelineId' | 'user' | 'rebake'>;

export interface IPipelineTriggerConfigProps {
  status: string[];
  trigger: IPipelineTrigger;
  application: Application;
  pipelineId: string;
  triggerUpdated: (trigger: IPipelineTriggerConfig) => void;
}

export interface IPipelineTriggerState {
  applications: string[];
  pipelines: IPipeline[];
  pipelinesLoaded: boolean;
}

export class PipelineTrigger extends React.Component<IPipelineTriggerConfigProps, IPipelineTriggerState> {
  private destroy$ = new Subject();
  private statusOptions = ['successful', 'failed', 'canceled'];

  public state: IPipelineTriggerState = {
    applications: [],
    pipelines: [],
    pipelinesLoaded: false,
  };

  public componentDidMount() {
    Observable.fromPromise(ApplicationReader.listApplications())
      .takeUntil(this.destroy$)
      .subscribe(
        applications => this.setState({ applications: applications.map(a => a.name).sort() }),
        () => this.setState({ applications: [] }),
      );

    const { application, status } = this.props.trigger;
    this.onUpdateTrigger({
      application: application || this.props.application.name,
      status: status || [],
    });

    this.init(application);
  }

  public componentWillUnmount() {
    this.destroy$.next();
  }

  private init = (application: string) => {
    const { pipelineId, trigger } = this.props;
    if (application) {
      Observable.fromPromise(PipelineConfigService.getPipelinesForApplication(application))
        .takeUntil(this.destroy$)
        .subscribe(pipelines => {
          pipelines = pipelines.filter(p => p.id !== pipelineId);
          if (!pipelines.find(p => p.id === trigger.pipeline)) {
            this.onUpdateTrigger({ pipeline: null });
          }
          this.setState({
            pipelines,
            pipelinesLoaded: true,
          });
        });
    }
  };

  private onUpdateTrigger = (update: Partial<IPipelineTriggerConfig>) => {
    this.props.triggerUpdated &&
      this.props.triggerUpdated({
        ...this.props.trigger,
        ...update,
      });
  };

  private PipelineTriggerContents = () => {
    const { application, pipeline, status } = this.props.trigger;
    const { applications, pipelines, pipelinesLoaded } = this.state;

    return (
      <>
        <FormField
          label="Application"
          value={application}
          onChange={e => {
            this.onUpdateTrigger({ application: e.target.value });
            this.init(e.target.value);
          }}
          input={props => (
            <ReactSelectInput {...props} mode="VIRTUALIZED" stringOptions={applications} placeholder="None" />
          )}
        />

        <FormField
          label="Pipeline"
          value={pipeline}
          onChange={e => this.onUpdateTrigger({ pipeline: e.target.value })}
          input={props =>
            application &&
            pipelinesLoaded && (
              <ReactSelectInput
                {...props}
                mode="VIRTUALIZED"
                options={pipelines.map(p => ({ label: p.name, value: p.id }))}
                placeholder="Select a pipeline..."
              />
            )
          }
        />

        <FormField
          label="Pipeline Status"
          value={status}
          onChange={e => this.onUpdateTrigger({ status: e.target.value })}
          input={props => <ChecklistInput {...props} stringOptions={this.statusOptions} inline={true} />}
        />
      </>
    );
  };

  public render() {
    const { PipelineTriggerContents } = this;
    return <BaseTrigger {...this.props} triggerContents={<PipelineTriggerContents />} />;
  }
}
