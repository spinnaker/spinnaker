import * as React from 'react';
import Select, { Option } from 'react-select';
import { Observable, Subject } from 'rxjs';

import { BaseTrigger } from 'core/pipeline';
import { BuildServiceType, IgorService } from 'core/ci/igor.service';
import { IBuildTrigger } from 'core/domain';
import { HelpField } from 'core/help';
import { Spinner } from 'core/widgets';
import { TextInput, Tooltip } from 'core/presentation';

export interface IBaseBuildTriggerConfigProps {
  buildTriggerType: BuildServiceType;
  trigger: IBuildTrigger;
  triggerUpdated: (trigger: IBuildTrigger) => void;
}

export interface IBaseBuildTriggerState {
  jobs: string[];
  jobsLoaded: boolean;
  jobsRefreshing: boolean;
  masters: string[];
  mastersLoaded: boolean;
  mastersRefreshing: boolean;
}

export class BaseBuildTrigger extends React.Component<IBaseBuildTriggerConfigProps, IBaseBuildTriggerState> {
  private destroy$ = new Subject();

  constructor(props: IBaseBuildTriggerConfigProps) {
    super(props);
    this.state = {
      jobs: [],
      jobsLoaded: false,
      jobsRefreshing: false,
      masters: [],
      mastersLoaded: false,
      mastersRefreshing: false,
    };
  }

  public componentDidMount = () => {
    this.initializeMasters();
  };

  private refreshMasters = () => {
    this.setState({
      mastersRefreshing: true,
    });
    this.initializeMasters();
  };

  private refreshJobs = () => {
    this.setState({
      jobsRefreshing: true,
    });
    this.updateJobsList(this.props.trigger.master);
  };

  private jobsUpdated = (jobs: string[]) => {
    this.setState({
      jobs,
      jobsLoaded: true,
      jobsRefreshing: false,
    });
    if (jobs.length && !jobs.includes(this.props.trigger.job)) {
      this.onUpdateTrigger({ job: '' });
    }
  };

  private updateJobsList = (master: string) => {
    if (master) {
      this.setState({
        jobsLoaded: false,
        jobs: [],
      });
      Observable.fromPromise(IgorService.listJobsForMaster(master))
        .takeUntil(this.destroy$)
        .subscribe(this.jobsUpdated, () => this.jobsUpdated([]));
    }
  };

  private onMasterUpdated = (option: Option<string>) => {
    const master = option.value;
    if (this.props.trigger.master !== master) {
      this.onUpdateTrigger({ master });
      this.updateJobsList(master);
    }
  };

  private initializeMasters = () => {
    Observable.fromPromise(IgorService.listMasters(this.props.buildTriggerType))
      .takeUntil(this.destroy$)
      .subscribe(this.mastersUpdated, () => this.mastersUpdated([]));
  };

  private onUpdateTrigger = (update: any) => {
    this.props.triggerUpdated &&
      this.props.triggerUpdated({
        ...this.props.trigger,
        ...update,
      });
  };

  private mastersUpdated = (masters: string[]) => {
    this.setState({
      masters,
      mastersLoaded: true,
      mastersRefreshing: false,
    });
    if (this.props.trigger.master) {
      this.refreshJobs();
    }
  };

  private Jobs = () => {
    const { job } = this.props.trigger;
    const { jobs, jobsLoaded } = this.state;
    return (
      <>
        {jobsLoaded && (
          <Select
            className="form-control input-sm"
            options={jobs.map(j => ({ label: j, value: j }))}
            placeholder={'Select a job...'}
            value={job}
            onChange={(option: Option<string>) => this.onUpdateTrigger({ job: option.value })}
          />
        )}
        {!jobsLoaded && (
          <div className="horizontal center">
            <div className="horizontal middle center">
              <Spinner size={'small'} />
            </div>
          </div>
        )}
      </>
    );
  };

  private BaseBuildTriggerContents = () => {
    const { Jobs } = this;
    const { master, propertyFile, type } = this.props.trigger;
    const { jobsRefreshing, masters, mastersRefreshing } = this.state;
    return (
      <>
        <div className="form-group">
          <label className="col-md-3 sm-label-right">Master</label>
          <div className="col-md-8">
            <Select
              className="form-control input-sm"
              onChange={this.onMasterUpdated}
              options={masters.map(m => ({ label: m, value: m }))}
              placeholder={'Select a master...'}
              value={master}
            />
          </div>
          <div className="col-md-1 text-center">
            <Tooltip placement="right" value={mastersRefreshing ? 'Masters refreshing' : 'Refresh masters list'}>
              <span
                className={'fa fa-sync-alt ' + (mastersRefreshing ? 'fa-spin' : '')}
                onClick={this.refreshMasters}
                style={{ cursor: 'pointer' }}
              />
            </Tooltip>
          </div>
        </div>
        <div className="form-group">
          <label className="col-md-3 sm-label-right">Job</label>
          <div className="col-md-8">
            {!master && <p className="form-control-static">(Select a master)</p>}
            {master && <Jobs />}
          </div>
          <div className="col-md-1 text-center">
            {master && (
              <Tooltip placement="right" value={mastersRefreshing ? 'Jobs refreshing' : 'Refresh job list'}>
                <span
                  className={'fa fa-sync-alt ' + (jobsRefreshing ? 'fa-spin' : '')}
                  onClick={this.refreshJobs}
                  style={{ cursor: 'pointer' }}
                />
              </Tooltip>
            )}
          </div>
        </div>
        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            <span>Property File </span>
            <HelpField id={`pipeline.config.${type}.trigger.propertyFile`} />
          </div>
          <div className="col-md-6">
            <TextInput
              className="form-control input-sm"
              onChange={(event: React.ChangeEvent<HTMLInputElement>) =>
                this.onUpdateTrigger({ propertyFile: event.target.value })
              }
              value={propertyFile}
            />
          </div>
        </div>
      </>
    );
  };

  public render() {
    const { BaseBuildTriggerContents } = this;
    return <BaseTrigger {...this.props} triggerContents={<BaseBuildTriggerContents />} />;
  }
}
