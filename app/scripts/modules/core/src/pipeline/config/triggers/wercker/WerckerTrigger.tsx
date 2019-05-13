import * as React from 'react';
import Select, { Option } from 'react-select';
import { Observable, Subject } from 'rxjs';

import { Application } from 'core/application';
import { BaseTrigger } from 'core/pipeline';
import { BuildServiceType, IgorService } from 'core/ci/igor.service';
import { IBaseBuildTriggerConfigProps, IBaseBuildTriggerState } from '../baseBuild/BaseBuildTrigger';
import { IWerckerTrigger } from 'core/domain';
import { Spinner } from 'core/widgets';
import { Tooltip } from 'core/presentation';

export interface IWerckerTriggerConfigProps extends IBaseBuildTriggerConfigProps {
  trigger: IWerckerTrigger;
  application: Application;
  pipelineId: string;
  triggerUpdated: (trigger: IWerckerTrigger) => void;
}

export interface IWerckerTriggerState extends IBaseBuildTriggerState {
  apps: string[];
  pipelines: any[];
}

export class WerckerTrigger extends React.Component<IWerckerTriggerConfigProps, IWerckerTriggerState> {
  private destroy$ = new Subject();

  constructor(props: IWerckerTriggerConfigProps) {
    super(props);
    this.state = {
      apps: [],
      jobs: [],
      jobsLoaded: false,
      jobsRefreshing: false,
      masters: [],
      mastersLoaded: false,
      mastersRefreshing: false,
      pipelines: [],
    };
  }

  public componentDidMount = () => {
    this.initializeMasters();
    this.updateJob(this.props.trigger.pipeline);
  };

  private initializeMasters = () => {
    Observable.fromPromise(IgorService.listMasters(BuildServiceType.Wercker))
      .takeUntil(this.destroy$)
      .subscribe(this.mastersUpdated, () => this.mastersUpdated([]));
  };

  private onMasterUpdated = (option: Option<string>) => {
    const master = option.value;
    if (this.props.trigger.master !== master) {
      this.onUpdateTrigger({ master });
      this.updateJobsList(master);
    }
  };

  private refreshMasters = () => {
    this.setState({
      mastersRefreshing: true,
    });
    this.initializeMasters();
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

  private refreshJobs = () => {
    this.setState({
      jobsRefreshing: true,
    });
    this.updateJobsList(this.props.trigger.master);
  };

  private jobsUpdated = (jobs: string[]) => {
    let { app } = this.props.trigger;
    const apps = jobs.map(job => job.substring(job.indexOf('/') + 1, job.lastIndexOf('/')));
    this.setState({
      apps,
      jobs,
      jobsLoaded: true,
      jobsRefreshing: false,
    });
    if (!apps.length || !apps.includes(app)) {
      app = '';
      this.onUpdateTrigger({
        app,
        job: '',
        pipeline: '',
      });
    }
    this.updatePipelinesList(app, jobs);
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

  private onAppUpdated = (option: Option<string>) => {
    const app = option.value;
    if (this.props.trigger.app !== app) {
      this.onUpdateTrigger({ app });
      this.updatePipelinesList(app, this.state.jobs);
    }
  };

  private onPipelineUpdated = (option: Option<string>) => {
    const pipeline = option.value;
    if (this.props.trigger.pipeline !== pipeline) {
      this.onUpdateTrigger({ pipeline });
      this.updateJob(pipeline);
    }
  };

  private updatePipelinesList(app: string, jobs: string[]): void {
    const { pipeline } = this.props.trigger;
    let pipelines: string[] = [];

    jobs.forEach(a => {
      if (app === a.substring(a.indexOf('/') + 1, a.lastIndexOf('/'))) {
        pipelines = pipelines.concat(a.substring(a.lastIndexOf('/') + 1));
      }
    });

    this.setState({ pipelines });

    if (!pipelines.length || (pipeline && !pipelines.includes(pipeline))) {
      this.onUpdateTrigger({
        job: '',
        pipeline: '',
      });
    }
  }

  private updateJob(pipeline: string): void {
    const { app } = this.props.trigger;
    if (app && pipeline) {
      this.onUpdateTrigger({ job: app + '/' + pipeline });
    }
  }

  private getAppsComponent = (): React.ReactNode => {
    const { app } = this.props.trigger;
    const { apps, jobsLoaded } = this.state;
    return (
      <>
        {jobsLoaded && (
          <Select
            className="form-control input-sm"
            options={apps.map(j => ({ label: j, value: j }))}
            placeholder={'Select an application...'}
            value={app}
            onChange={this.onAppUpdated}
          />
        )}
        {!jobsLoaded && (
          <div className="horizontal middle center">
            <Spinner size={'small'} />
          </div>
        )}
      </>
    );
  };

  private onUpdateTrigger = (update: any) => {
    this.props.triggerUpdated &&
      this.props.triggerUpdated({
        ...this.props.trigger,
        ...update,
      });
  };

  public WerkerTriggerContents() {
    const { app, master, pipeline } = this.props.trigger;
    const { jobsRefreshing, masters, mastersRefreshing, pipelines } = this.state;
    return (
      <>
        <div className="form-group">
          <label className="col-md-3 sm-label-right">Master</label>
          <div className="col-md-6">
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
          <label className="col-md-3 sm-label-right">Application</label>
          <div className="col-md-6">
            {!master && <p className="form-control-static">(Select a master)</p>}
            {master && this.getAppsComponent()}
          </div>
          <div className="col-md-1 text-center">
            {master && (
              <Tooltip placement="right" value={mastersRefreshing ? 'Apps refreshing' : 'Refresh app list'}>
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
          <label className="col-md-3 sm-label-right">Pipeline</label>
          <div className="col-md-8">
            {!app && <p className="form-control-static">(Select an application)</p>}
            {app && (
              <Select
                className="form-control input-sm"
                options={pipelines.map(p => ({ label: p, value: p }))}
                placeholder={'Select a pipeline...'}
                value={pipeline}
                onChange={this.onPipelineUpdated}
              />
            )}
          </div>
        </div>
      </>
    );
  }

  public render() {
    const { WerkerTriggerContents } = this;
    return <BaseTrigger {...this.props} triggerContents={<WerkerTriggerContents />} />;
  }
}
