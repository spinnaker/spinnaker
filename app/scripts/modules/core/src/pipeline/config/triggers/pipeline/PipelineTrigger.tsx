import * as React from 'react';
import Select, { Option } from 'react-select';

import { Observable } from 'rxjs';

import { ApplicationReader } from 'core/application/service/ApplicationReader';
import { BaseTrigger } from 'core/pipeline';
import { IPipeline, IPipelineTrigger } from 'core/domain';
import { PipelineConfigService } from 'core/pipeline/config/services/PipelineConfigService';
import { Checklist } from 'core/forms';
import { Application } from '@spinnaker/core';
import { Subject } from 'rxjs/Subject';

export interface IPipelineTriggerConfigProps {
  status: string[];
  trigger: IPipelineTrigger;
  application: Application;
  pipelineId: string;
  triggerUpdated: (trigger: IPipelineTrigger) => void;
}

export interface IPipelineTriggerState {
  applications: string[];
  pipelines: IPipeline[];
  pipelinesLoaded: boolean;
  useDefaultParameters: { [k: string]: boolean };
  userSuppliedParameters: { [k: string]: string };
}

export class PipelineTrigger extends React.Component<IPipelineTriggerConfigProps, IPipelineTriggerState> {
  private destroy$ = new Subject();
  private statusOptions = ['successful', 'failed', 'canceled'];

  constructor(props: IPipelineTriggerConfigProps) {
    super(props);

    this.state = {
      applications: [],
      pipelines: [],
      pipelinesLoaded: false,
      useDefaultParameters: {},
      userSuppliedParameters: {},
    };
  }

  public componentDidMount = () => {
    Observable.fromPromise(ApplicationReader.listApplications())
      .takeUntil(this.destroy$)
      .subscribe(
        applications => this.setState({ applications: applications.map(a => a.name).sort() }),
        () => this.setState({ applications: [] }),
      );

    const { application } = this.props.trigger;
    this.onUpdateTrigger({
      application: application || this.props.application.name,
      status: status || [],
    });

    this.init(application);
  };

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

  private onUpdateTrigger = (update: any) => {
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
        <div className="form-horizontal">
          <div className="form-group">
            <label className="col-md-3 sm-label-right">Application</label>
            <div className="col-md-6">
              <Select
                className="form-control input-sm"
                options={applications.map(j => ({ label: j, value: j }))}
                placeholder={'None'}
                value={application}
                onChange={(option: Option<string>) => {
                  const a = option.value;
                  this.onUpdateTrigger({ application: a });
                  this.init(a);
                }}
              />
            </div>
          </div>
          <div className="form-group">
            <label className="col-md-3 sm-label-right">Pipeline</label>
            <div className="col-md-6">
              {application && pipelinesLoaded && (
                <Select
                  className="form-control input-sm"
                  onChange={(option: Option<string>) => this.onUpdateTrigger({ pipeline: option.value })}
                  options={pipelines.map(p => ({ label: p.id, value: p.id }))}
                  placeholder={'Select a pipeline...'}
                  value={pipeline}
                />
              )}
            </div>
          </div>
          <div className="form-group">
            <label className="col-md-3 sm-label-right">Pipeline Status</label>
            <div className="col-md-6">
              <Checklist
                inline={true}
                items={new Set(this.statusOptions)}
                checked={new Set(status)}
                onChange={(s: Set<string>) => this.onUpdateTrigger({ status: s })}
              />
            </div>
          </div>
        </div>
      </>
    );
  };

  public render() {
    const { PipelineTriggerContents } = this;
    return <BaseTrigger {...this.props} triggerContents={<PipelineTriggerContents />} />;
  }
}
