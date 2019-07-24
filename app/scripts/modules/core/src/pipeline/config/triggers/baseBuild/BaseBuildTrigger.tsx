import * as React from 'react';
import { Observable, Subject } from 'rxjs';

import { BaseTrigger } from 'core/pipeline';
import { BuildServiceType, IgorService } from 'core/ci/igor.service';
import { IBuildTrigger } from 'core/domain';
import { HelpField } from 'core/help';
import { FormField, TextInput } from 'core/presentation';

import { RefreshableReactSelectInput } from '../RefreshableReactSelectInput';

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

  public componentDidMount() {
    this.initializeMasters();
  }

  public componentWillUnmount() {
    this.destroy$.next();
  }

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

  private onMasterUpdated = (master: string) => {
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

  private BaseBuildTriggerContents = () => {
    const { master, job, propertyFile, type } = this.props.trigger;
    const { jobsRefreshing, masters, mastersRefreshing } = this.state;
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
              placeholder="Select a master..."
              clearable={false}
              isLoading={mastersRefreshing}
              onRefreshClicked={this.refreshMasters}
              refreshButtonTooltipText={mastersRefreshing ? 'Masters refreshing' : 'Refresh masters list'}
            />
          )}
        />

        <FormField
          label="Job"
          value={master}
          onChange={e => this.onMasterUpdated(e.target.value)}
          input={props => (
            <RefreshableReactSelectInput
              {...props}
              mode="VIRTUALIZED"
              value={job}
              onChange={e => this.onUpdateTrigger({ job: e.target.value })}
              options={this.state.jobs.map(j => ({ label: j, value: j }))}
              disabled={!master}
              placeholder={'Select a job...'}
              clearable={true}
              isLoading={mastersRefreshing || jobsRefreshing}
              onRefreshClicked={this.refreshJobs}
              refreshButtonTooltipText={mastersRefreshing || jobsRefreshing ? 'Jobs refreshing' : 'Refresh job list'}
            />
          )}
        />

        <FormField
          label="Property File"
          help={<HelpField id={`pipeline.config.${type}.trigger.propertyFile`} />}
          value={propertyFile}
          onChange={e => this.onUpdateTrigger({ propertyFile: e.target.value })}
          input={props => <TextInput {...props} />}
        />
      </>
    );
  };

  public render() {
    const { BaseBuildTriggerContents } = this;
    return <BaseTrigger {...this.props} triggerContents={<BaseBuildTriggerContents />} />;
  }
}
