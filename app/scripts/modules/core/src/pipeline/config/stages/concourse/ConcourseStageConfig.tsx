import React from 'react';
import Select, { Option } from 'react-select';

import { BuildServiceType, IgorService } from '../../../../ci';

import { IStageConfigProps, StageConfigField } from '../common';
import { ConcourseService } from '../../triggers/concourse/concourse.service';

export interface IConcourseStageConfigState {
  masters: string[];
  teams: string[];
  pipelines: string[];
  resourceNames: string[];
}

export interface IConcourseStage {
  master?: string;
  teamName?: string;
  pipelineName?: string;
  resourceName?: string;
}

export class ConcourseStageConfig extends React.Component<IStageConfigProps, IConcourseStageConfigState> {
  constructor(props: IStageConfigProps) {
    super(props);
    const { master, teamName, pipelineName, resourceName } = props.stage as IConcourseStage;
    this.state = {
      masters: master ? [master] : [],
      teams: teamName ? [teamName] : [],
      pipelines: pipelineName ? [pipelineName] : [],
      resourceNames: resourceName ? [resourceName] : [],
    };
  }

  public componentDidMount(): void {
    IgorService.listMasters(BuildServiceType.Concourse).then((masters: string[]) => {
      this.setState({ masters });
    });

    this.fetchAvailableTeams();
    this.fetchAvailablePipelines();
    this.fetchAvailableResourceNames();
  }

  public render() {
    const { masters, teams, pipelines, resourceNames } = this.state;
    const { master, teamName, pipelineName, resourceName } = this.getStage();

    return (
      <>
        <StageConfigField label="Build Service">
          <Select
            value={master}
            placeholder="Select a build service..."
            onChange={this.onMasterChanged}
            options={masters.map((name: string) => ({ label: name, value: name }))}
            clearable={false}
          />
        </StageConfigField>
        <StageConfigField label="Team">
          {!master && <p className="form-control-static">(Select a build service)</p>}
          {master && (
            <Select
              value={teamName}
              placeholder="Select a team..."
              onChange={this.onTeamChanged}
              options={teams.map((name: string) => ({ label: name, value: name }))}
              clearable={false}
            />
          )}
        </StageConfigField>
        <StageConfigField label="Pipeline">
          {!teamName && <p className="form-control-static">(Select a build service and team)</p>}
          {teamName && (
            <Select
              value={pipelineName}
              placeholder="Select a pipeline..."
              onChange={this.onPipelineChanged}
              options={pipelines.map((name: string) => ({ label: name, value: name }))}
              clearable={false}
            />
          )}
        </StageConfigField>
        <StageConfigField label="Resource Name">
          {!pipelineName && <p className="form-control-static">(Select a build service, team, and pipeline)</p>}
          {pipelineName && (
            <Select
              value={resourceName}
              placeholder="Select a resource..."
              onChange={this.onResourceNameChanged}
              options={resourceNames.map((name: string) => ({ label: name, value: name }))}
              clearable={false}
            />
          )}
        </StageConfigField>
      </>
    );
  }

  private onMasterChanged = (option: Option<string>) => {
    const stage = this.getStage();
    if (stage.master === option.value) {
      return;
    }
    this.props.updateStageField({
      master: option.value,
      team: null,
      pipelineName: null,
      resourceName: null,
    });
    this.fetchAvailableTeams();
  };

  private onTeamChanged = (option: Option<string>) => {
    const stage = this.getStage();
    if (stage.teamName === option.value) {
      return;
    }
    this.props.updateStageField({
      teamName: option.value,
      pipelineName: null,
      resourceName: null,
    });
    this.fetchAvailablePipelines();
  };

  private onPipelineChanged = (option: Option<string>) => {
    const stage = this.getStage();
    if (stage.pipelineName === option.value) {
      return;
    }
    this.props.updateStageField({
      pipelineName: option.value,
      resourceName: null,
    });
    this.fetchAvailableResourceNames();
  };

  private onResourceNameChanged = (option: Option<string>) => {
    const stage = this.getStage();
    if (stage.resourceName === option.value) {
      return;
    }
    this.props.updateStageField({ resourceName: option.value });
    this.props.stageFieldUpdated();
  };

  private fetchAvailableTeams = () => {
    const { master } = this.getStage();
    if (master) {
      ConcourseService.listTeamsForMaster(master).then((teams) => this.setState({ teams: teams }));
    }
  };

  private fetchAvailablePipelines = () => {
    const { master, teamName } = this.getStage();
    if (master && teamName) {
      ConcourseService.listPipelinesForTeam(master, teamName).then((pipelines) =>
        this.setState({ pipelines: pipelines }),
      );
    }
  };

  private fetchAvailableResourceNames = () => {
    const { master, teamName, pipelineName } = this.getStage();
    if (master && teamName && pipelineName) {
      ConcourseService.listResourcesForPipeline(master, teamName, pipelineName).then((resourceNames) =>
        this.setState({ resourceNames: resourceNames }),
      );
    }
  };

  private getStage() {
    return this.props.stage as IConcourseStage;
  }
}
