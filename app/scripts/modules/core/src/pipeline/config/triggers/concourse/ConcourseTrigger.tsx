import * as React from 'react';
import { Observable, Subject } from 'rxjs';
import { Option } from 'react-select';

import { BaseTrigger } from 'core/pipeline';
import { IConcourseTrigger } from 'core/domain';
import { BuildServiceType, IgorService } from 'core/ci';
import { FormField, ReactSelectInput } from 'core/presentation';

import { ConcourseService } from './concourse.service';

export interface IConcourseTriggerConfigProps {
  trigger: IConcourseTrigger;
  triggerUpdated: (trigger: IConcourseTrigger) => void;
}

export interface IConcourseTriggerConfigState {
  masters: string[];
  teams: string[];
  pipelines: string[];
  jobs: string[];
}

export class ConcourseTrigger extends React.Component<IConcourseTriggerConfigProps, IConcourseTriggerConfigState> {
  private destroy$ = new Subject();

  constructor(props: IConcourseTriggerConfigProps) {
    super(props);
    const { master, team, project, job } = this.props.trigger;
    this.state = {
      masters: master ? [master] : [],
      teams: team ? [team] : [],
      pipelines: project ? [project.split('/').pop()] : [],
      jobs: job ? [job] : [],
    };
  }

  public componentDidMount() {
    Observable.fromPromise(IgorService.listMasters(BuildServiceType.Concourse))
      .takeUntil(this.destroy$)
      .subscribe((masters: string[]) => {
        this.setState({ masters });
      });

    const { trigger } = this.props;
    this.fetchAvailableTeams(trigger);
    this.fetchAvailablePipelines(trigger);
    this.fetchAvailableJobs(trigger);
  }

  private onUpdateTrigger = (update: any) => {
    this.props.triggerUpdated &&
      this.props.triggerUpdated({
        ...this.props.trigger,
        ...update,
      });
  };

  private onTeamChanged = (option: Option<string>) => {
    const team = option.value;
    if (this.props.trigger.team !== team) {
      const trigger = {
        ...this.props.trigger,
        job: '',
        jobName: '',
        project: '',
        team,
      };
      this.fetchAvailablePipelines(trigger);
      this.onUpdateTrigger(trigger);
    }
  };

  private onPipelineChanged = (option: Option<string>) => {
    const p = option.value;
    const { project, team } = this.props.trigger;

    if (!project || project.split('/').pop() !== p) {
      const trigger = {
        ...this.props.trigger,
        job: '',
        jobName: '',
        project: `${team}/${p}`,
      };
      this.fetchAvailableJobs(trigger);
      this.onUpdateTrigger(trigger);
    }
  };

  private onJobChanged = (option: Option<string>) => {
    const jobName = option.value;

    if (this.props.trigger.jobName !== jobName) {
      const { project } = this.props.trigger;
      const trigger = {
        ...this.props.trigger,
        jobName,
        job: `${project}/${jobName}`,
      };
      this.fetchAvailableJobs(trigger);
      this.onUpdateTrigger(trigger);
    }
  };

  private onMasterChanged = (option: Option<string>) => {
    const master = option.value;

    if (this.props.trigger.master !== master) {
      const trigger = {
        ...this.props.trigger,
        master,
      };
      this.fetchAvailableTeams(trigger);
      this.onUpdateTrigger(trigger);
    }
  };

  private fetchAvailableTeams = (trigger: IConcourseTrigger) => {
    const { master } = trigger;
    if (master) {
      Observable.fromPromise(ConcourseService.listTeamsForMaster(master))
        .takeUntil(this.destroy$)
        .subscribe(teams => this.setState({ teams }));
    }
  };

  private fetchAvailablePipelines = (trigger: IConcourseTrigger) => {
    const { master, team } = trigger;
    if (master && team) {
      Observable.fromPromise(ConcourseService.listPipelinesForTeam(master, team))
        .takeUntil(this.destroy$)
        .subscribe(pipelines => this.setState({ pipelines }));
    }
  };

  private fetchAvailableJobs = (trigger: IConcourseTrigger) => {
    const { master, project, team } = trigger;
    if (master && team && project) {
      const pipeline = project.split('/').pop();
      Observable.fromPromise(ConcourseService.listJobsForPipeline(master, team, pipeline))
        .takeUntil(this.destroy$)
        .subscribe(jobs => this.setState({ jobs }));
    }
  };

  private ConcourseTriggerContents = () => {
    const { jobName, team, project, master } = this.props.trigger;
    const { jobs, pipelines, teams, masters } = this.state;
    const pipeline = project && project.split('/').pop();

    const jobOptions = jobs.map((name: string) => {
      const jobNameOption = name.split('/').pop();
      return { label: jobNameOption, value: jobNameOption };
    });

    return (
      <>
        <FormField
          label="Master"
          value={master}
          onChange={e => this.onMasterChanged(e.target.value)}
          input={props => (
            <ReactSelectInput {...props} placeholder="Select a master..." stringOptions={masters} clearable={false} />
          )}
        />

        <FormField
          label="Team"
          value={team}
          onChange={e => this.onTeamChanged(e.target.value)}
          input={props =>
            master ? (
              <ReactSelectInput {...props} placeholder="Select a team..." stringOptions={teams} clearable={false} />
            ) : (
              <p className="form-control-static">(Select a master)</p>
            )
          }
        />

        <FormField
          label="Pipeline"
          value={pipeline}
          onChange={e => this.onPipelineChanged(e.target.value)}
          input={props =>
            team ? (
              <ReactSelectInput
                {...props}
                placeholder="Select a pipeline..."
                stringOptions={pipelines}
                clearable={false}
              />
            ) : (
              <p className="form-control-static">(Select a master and team)</p>
            )
          }
        />

        <FormField
          label="Job"
          value={jobName}
          onChange={e => this.onJobChanged(e.target.value)}
          input={props =>
            pipeline ? (
              <ReactSelectInput {...props} placeholder="Select a job..." options={jobOptions} clearable={false} />
            ) : (
              <p className="form-control-static">(Select a master, team, and pipeline)</p>
            )
          }
        />
      </>
    );
  };

  public render() {
    const { ConcourseTriggerContents } = this;
    return <BaseTrigger {...this.props} triggerContents={<ConcourseTriggerContents />} />;
  }
}
