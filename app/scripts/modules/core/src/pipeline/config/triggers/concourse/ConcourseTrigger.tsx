import * as React from 'react';
import Select, { Option } from 'react-select';

import { BaseTrigger } from 'core/pipeline';
import { IConcourseTrigger } from 'core/domain';
import { Observable } from 'rxjs';
import { BuildServiceType, IgorService } from 'core/ci';
import { ConcourseService } from './concourse.service';
import { Subject } from 'rxjs/Subject';

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

  public componentDidMount(): void {
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

    return (
      <>
        <div className="form-group">
          <label className="col-md-3 sm-label-right">Master</label>
          <div className="col-md-8">
            <Select
              value={master}
              placeholder="Select a master..."
              onChange={this.onMasterChanged}
              options={masters.map((name: string) => ({ label: name, value: name }))}
              clearable={false}
            />
          </div>
        </div>
        <div className="form-group">
          <label className="col-md-3 sm-label-right">Team</label>
          <div className="col-md-8">
            {!master && <p className="form-control-static">(Select a master)</p>}
            {master && (
              <Select
                value={team}
                placeholder="Select a team..."
                onChange={this.onTeamChanged}
                options={teams.map((name: string) => ({ label: name, value: name }))}
                clearable={false}
              />
            )}
          </div>
        </div>
        <div className="form-group">
          <label className="col-md-3 sm-label-right">Pipeline</label>
          <div className="col-md-8">
            {!team && <p className="form-control-static">(Select a master and team)</p>}
            {team && (
              <Select
                value={pipeline}
                placeholder="Select a pipeline..."
                onChange={this.onPipelineChanged}
                options={pipelines.map((name: string) => ({ label: name, value: name }))}
                clearable={false}
              />
            )}
          </div>
        </div>
        <div className="form-group">
          <label className="col-md-3 sm-label-right">Job</label>
          <div className="col-md-8">
            {!pipeline && <p className="form-control-static">(Select a master, team, and pipeline)</p>}
            {pipeline && (
              <Select
                value={jobName}
                placeholder="Select a job..."
                onChange={this.onJobChanged}
                options={jobs.map((name: string) => {
                  const jobNameOption = name.split('/').pop();
                  return { label: jobNameOption, value: jobNameOption };
                })}
                clearable={false}
              />
            )}
          </div>
        </div>
      </>
    );
  };

  public render() {
    const { ConcourseTriggerContents } = this;
    return <BaseTrigger {...this.props} triggerContents={<ConcourseTriggerContents />} />;
  }
}
