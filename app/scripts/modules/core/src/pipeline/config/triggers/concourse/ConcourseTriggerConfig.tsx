import * as React from 'react';
import Select, { Option } from 'react-select';

import { IConcourseTrigger } from 'core/domain';
import { ServiceAccountReader } from 'core/serviceAccount';
import { SETTINGS } from 'core/config/settings';
import { BuildServiceType, IgorService } from 'core/ci';
import { ITriggerConfigProps } from '../ITriggerConfigProps';
import { RunAsUser } from '../RunAsUser';
import { ConcourseService } from './concourse.service';

export interface IConcourseTriggerConfigProps extends ITriggerConfigProps {
  trigger: IConcourseTrigger;
}

export interface IConcourseTriggerConfigState {
  masters: string[];
  teams: string[];
  pipelines: string[];
  jobs: string[];
  serviceAccounts: string[];
}

export class ConcourseTriggerConfig extends React.Component<
  IConcourseTriggerConfigProps,
  IConcourseTriggerConfigState
> {
  constructor(props: IConcourseTriggerConfigProps) {
    super(props);
    const { master, team, project, job } = this.props.trigger;
    this.state = {
      masters: master ? [master] : [],
      teams: team ? [team] : [],
      pipelines: project ? [project.split('/').pop()] : [],
      jobs: job ? [job] : [],
      serviceAccounts: [],
    };
  }

  public componentDidMount(): void {
    if (SETTINGS.feature.fiat) {
      ServiceAccountReader.getServiceAccounts().then(accounts => {
        this.setState({ serviceAccounts: accounts || [] });
      });
    }

    IgorService.listMasters(BuildServiceType.Concourse).then((masters: string[]) => {
      this.setState({ masters });
    });

    this.fetchAvailableTeams();
    this.fetchAvailablePipelines();
    this.fetchAvailableJobs();
  }

  public render() {
    const { jobName, team, project, master, runAsUser } = this.props.trigger;
    const { jobs, pipelines, teams, masters, serviceAccounts } = this.state;
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
        {SETTINGS.feature.fiatEnabled && (
          <div className="form-group">
            <RunAsUser serviceAccounts={serviceAccounts} value={runAsUser} onChange={this.onRunAsUserChanged} />
            />
          </div>
        )}
      </>
    );
  }

  private onRunAsUserChanged = (runAsUser: string) => {
    this.props.trigger.runAsUser = runAsUser;
    this.props.fieldUpdated();
  };

  private onTeamChanged = (option: Option<string>) => {
    const trigger = this.props.trigger;
    if (trigger.team === option.value) {
      return;
    }
    delete trigger.job;
    delete trigger.project;
    delete trigger.jobName;
    trigger.team = option.value;
    this.props.fieldUpdated();
    this.fetchAvailablePipelines();
  };

  private onPipelineChanged = (option: Option<string>) => {
    const trigger = this.props.trigger;
    if (trigger.project && trigger.project.split('/').pop() === option.value) {
      return;
    }
    delete trigger.job;
    delete trigger.jobName;
    trigger.project = `${trigger.team}/${option.value}`;
    this.props.fieldUpdated();
    this.fetchAvailableJobs();
  };

  private onJobChanged = (option: Option<string>) => {
    const trigger = this.props.trigger;
    if (trigger.jobName === option.value) {
      return;
    }
    trigger.jobName = option.value;
    trigger.job = `${trigger.project}/${trigger.jobName}`;
    this.props.fieldUpdated();
    this.fetchAvailableJobs();
  };

  private onMasterChanged = (option: Option<string>) => {
    const trigger = this.props.trigger;
    if (trigger.master === option.value) {
      return;
    }
    trigger.master = option.value;
    this.props.fieldUpdated();
    this.fetchAvailableTeams();
  };

  private fetchAvailableTeams = () => {
    const { master } = this.props.trigger;
    if (master) {
      ConcourseService.listTeamsForMaster(master).then(teams => this.setState({ teams: teams }));
    }
  };

  private fetchAvailablePipelines = () => {
    const { master, team } = this.props.trigger;
    if (master && team) {
      ConcourseService.listPipelinesForTeam(master, team).then(pipelines => this.setState({ pipelines: pipelines }));
    }
  };

  private fetchAvailableJobs = () => {
    const { master, team, project } = this.props.trigger;
    if (master && team && project) {
      const pipeline = project.split('/').pop();
      ConcourseService.listJobsForPipeline(master, team, pipeline).then(jobs => this.setState({ jobs: jobs }));
    }
  };
}
