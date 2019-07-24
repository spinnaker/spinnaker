import * as React from 'react';
import { Observable, Subject } from 'rxjs';

import { Application } from 'core/application';
import { BaseTrigger } from 'core/pipeline';
import { BuildServiceType, IgorService } from 'core/ci/igor.service';
import { IBaseBuildTriggerConfigProps, IBaseBuildTriggerState } from '../baseBuild/BaseBuildTrigger';
import { IWerckerTrigger } from 'core/domain';
import { FormField, ReactSelectInput } from 'core/presentation';

import { RefreshableReactSelectInput } from '../RefreshableReactSelectInput';

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

  public componentDidMount() {
    this.refreshMasters();
    this.updateJob(this.props.trigger.pipeline);
  }

  public componentWillUnmount() {
    this.destroy$.next();
  }

  private initializeMasters = () => {
    Observable.fromPromise(IgorService.listMasters(BuildServiceType.Wercker))
      .takeUntil(this.destroy$)
      .subscribe(this.mastersUpdated, () => this.mastersUpdated([]));
  };

  private onMasterUpdated = (master: string) => {
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
        jobsRefreshing: true,
        jobsLoaded: false,
        jobs: [],
      });
      Observable.fromPromise(IgorService.listJobsForMaster(master))
        .takeUntil(this.destroy$)
        .subscribe(this.jobsUpdated, () => this.jobsUpdated([]));
    }
  };

  private onAppUpdated = (app: string) => {
    if (this.props.trigger.app !== app) {
      this.onUpdateTrigger({ app });
      this.updatePipelinesList(app, this.state.jobs);
    }
  };

  private onPipelineUpdated = (pipeline: string) => {
    if (this.props.trigger.pipeline !== pipeline) {
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
      this.onUpdateTrigger({ pipeline, job: app + '/' + pipeline });
    }
  }

  private onUpdateTrigger = (update: any) => {
    this.props.triggerUpdated && this.props.triggerUpdated(update);
  };

  public WerkerTriggerContents = () => {
    const { app, master, pipeline } = this.props.trigger;
    const { apps, jobsRefreshing, masters, mastersRefreshing, pipelines } = this.state;
    return (
      <>
        <FormField
          label="Master"
          value={master}
          onChange={e => this.onMasterUpdated(e.target.value)}
          input={props => (
            <RefreshableReactSelectInput
              {...props}
              stringOptions={masters}
              placeholder={'Select a master...'}
              isLoading={mastersRefreshing}
              onRefreshClicked={() => this.refreshMasters()}
              refreshButtonTooltipText={jobsRefreshing ? 'Masters refreshing' : 'Refresh masters list'}
            />
          )}
        />

        <FormField
          label="Application"
          value={app}
          onChange={e => this.onAppUpdated(e.target.value)}
          input={props =>
            !master ? (
              <p className="form-control-static">(Select a master)</p>
            ) : (
              <RefreshableReactSelectInput
                {...props}
                stringOptions={apps}
                placeholder={'Select an application...'}
                isLoading={mastersRefreshing || jobsRefreshing}
                onRefreshClicked={() => this.refreshJobs()}
                refreshButtonTooltipText={jobsRefreshing ? 'Apps refreshing' : 'Refresh app list'}
              />
            )
          }
        />

        <FormField
          label="Pipeline"
          value={pipeline}
          onChange={e => this.onPipelineUpdated(e.target.value)}
          input={props =>
            !app ? (
              <p className="form-control-static">(Select an application)</p>
            ) : (
              <ReactSelectInput {...props} stringOptions={pipelines} placeholder="Select a pipeline" />
            )
          }
        />
      </>
    );
  };

  public render() {
    const { WerkerTriggerContents } = this;
    return <BaseTrigger {...this.props} triggerContents={<WerkerTriggerContents />} />;
  }
}
