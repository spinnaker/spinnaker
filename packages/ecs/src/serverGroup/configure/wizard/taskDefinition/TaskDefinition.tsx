import { module } from 'angular';
import { isEqual, uniqWith } from 'lodash';
import React from 'react';
import { Alert } from 'react-bootstrap';
import { Option } from 'react-select';
import { react2angular } from 'react2angular';

import {
  ArtifactTypePatterns,
  CheckboxInput,
  HelpField,
  IArtifact,
  IExpectedArtifact,
  StageArtifactSelectorDelegate,
  StageConfigField,
  TetheredSelect,
  withErrorBoundary,
} from '@spinnaker/core';

import {
  IEcsContainerMapping,
  IEcsDockerImage,
  IEcsServerGroupCommand,
  IEcsTargetGroupMapping,
  IEcsTaskDefinitionArtifact,
} from '../../serverGroupConfiguration.service';

export interface ITaskDefinitionProps {
  command: IEcsServerGroupCommand;
  notifyAngular: (key: string, value: any) => void;
  configureCommand: (query: string) => PromiseLike<void>;
}

interface ITaskDefinitionState {
  taskDefArtifact: IEcsTaskDefinitionArtifact;
  taskDefArtifactAccount: string;
  containerMappings: IEcsContainerMapping[];
  targetGroupMappings: IEcsTargetGroupMapping[];
  dockerImages: IEcsDockerImage[];
  targetGroupsAvailable: string[];
  loadBalancedContainer: string;
  evaluateTaskDefinitionArtifactExpressions: boolean;
}

export class TaskDefinition extends React.Component<ITaskDefinitionProps, ITaskDefinitionState> {
  constructor(props: ITaskDefinitionProps) {
    super(props);
    const cmd = this.props.command;
    let defaultContainer = '';
    if (cmd.containerMappings && cmd.containerMappings.length > 0) {
      defaultContainer = cmd.containerMappings[0].containerName;
    }

    let defaultTargetGroup: IEcsTargetGroupMapping[] = [];
    if (cmd.targetGroupMappings && cmd.targetGroupMappings.length > 0) {
      defaultTargetGroup = cmd.targetGroupMappings;
    }

    if (cmd.targetGroup && cmd.targetGroup.length > 0) {
      defaultTargetGroup.push({
        containerName: cmd.loadBalancedContainer || defaultContainer,
        targetGroup: cmd.targetGroup,
        containerPort: cmd.containerPort,
      });
      cmd.targetGroup = '';
      cmd.loadBalancedContainer = '';
    }

    cmd.targetGroupMappings = uniqWith(defaultTargetGroup, isEqual);

    this.state = {
      taskDefArtifact: cmd.taskDefinitionArtifact,
      containerMappings: cmd.containerMappings ? cmd.containerMappings : [],
      targetGroupMappings: cmd.targetGroupMappings,
      targetGroupsAvailable: cmd.backingData && cmd.backingData.filtered ? cmd.backingData.filtered.targetGroups : [],
      dockerImages: cmd.backingData && cmd.backingData.filtered ? cmd.backingData.filtered.images : [],
      loadBalancedContainer: cmd.loadBalancedContainer || defaultContainer,
      taskDefArtifactAccount: cmd.taskDefinitionArtifactAccount,
      evaluateTaskDefinitionArtifactExpressions: cmd.evaluateTaskDefinitionArtifactExpressions,
    };
  }

  // TODO: Separate docker image component used by both TaskDefinition and Container
  public componentDidMount() {
    this.props.configureCommand('1').then(() => {
      this.setState({
        dockerImages: this.props.command.backingData.filtered.images,
        targetGroupsAvailable: this.props.command.backingData.filtered.targetGroups,
      });
    });
  }

  private getIdToImageMap = (): Map<string, IEcsDockerImage> => {
    const imageIdToDescription = new Map<string, IEcsDockerImage>();
    this.props.command.backingData.filtered.images.forEach((e) => {
      imageIdToDescription.set(e.imageId, e);
    });

    return imageIdToDescription;
  };

  private getEmptyImageDescription = (): IEcsDockerImage => {
    return {
      imageId: '',
      message: '',
      fromTrigger: false,
      fromContext: false,
      stageId: '',
      imageLabelOrSha: '',
      account: '',
      registry: '',
      repository: '',
      tag: '',
    };
  };

  private excludedArtifactTypePatterns = [
    ArtifactTypePatterns.KUBERNETES,
    ArtifactTypePatterns.DOCKER_IMAGE,
    ArtifactTypePatterns.FRONT50_PIPELINE_TEMPLATE,
  ];

  private onExpectedArtifactSelected = (expectedArtifactId: string): void => {
    const selectedArtifact = { artifactId: expectedArtifactId };
    this.props.notifyAngular('taskDefinitionArtifact', selectedArtifact);
    this.setState({ taskDefArtifact: selectedArtifact });
  };

  private onArtifactEdited = (artifact: IArtifact): void => {
    const newArtifact = { artifact: artifact };
    this.props.notifyAngular('taskDefinitionArtifact', newArtifact);
    this.setState({ taskDefArtifact: newArtifact });
  };

  private pushMapping = () => {
    const conMaps = this.state.containerMappings;
    conMaps.push({ containerName: '', imageDescription: this.getEmptyImageDescription() });
    this.setState({ containerMappings: conMaps });
  };

  private pushTargetGroupMapping = () => {
    const targetMaps = this.state.targetGroupMappings;
    targetMaps.push({ containerName: '', targetGroup: '', containerPort: 80 });
    this.setState({ targetGroupMappings: targetMaps });
  };

  private updateContainerMappingName = (index: number, newName: string) => {
    const currentMappings = this.state.containerMappings;
    const targetMapping = currentMappings[index];
    targetMapping.containerName = newName;
    this.props.notifyAngular('containerMappings', currentMappings);
    this.setState({ containerMappings: currentMappings });
  };

  private updateContainerMappingImage = (index: number, newImage: Option<string>) => {
    const imageMap = this.getIdToImageMap();
    let newImageDescription = imageMap.get(newImage.value);

    if (!newImageDescription) {
      newImageDescription = this.getEmptyImageDescription();
    }
    const currentMappings = this.state.containerMappings;
    const targetMapping = currentMappings[index];
    targetMapping.imageDescription = newImageDescription;
    this.props.notifyAngular('containerMappings', currentMappings);
    this.setState({ containerMappings: currentMappings });
  };

  private updateTargetGroupMappingTargetGroup = (index: number, newTargetGroup: Option<string>) => {
    const currentMappings = this.state.targetGroupMappings;
    const targetMapping = currentMappings[index];
    targetMapping.targetGroup = newTargetGroup.value;
    this.props.notifyAngular('targetGroupMappings', currentMappings);
    this.setState({ targetGroupMappings: currentMappings });
  };

  private updateTargetGroupMappingContainer = (index: number, targetContainer: string) => {
    const currentMappings = this.state.targetGroupMappings;
    const targetMapping = currentMappings[index];
    targetMapping.containerName = targetContainer;
    this.props.notifyAngular('targetGroupMappings', currentMappings);
    this.setState({ targetGroupMappings: currentMappings });
  };

  private updateTargetGroupMappingPort = (index: number, targetPort: number) => {
    const currentMappings = this.state.targetGroupMappings;
    const targetMapping = currentMappings[index];
    targetMapping.containerPort = targetPort;
    this.props.notifyAngular('targetGroupMappings', currentMappings);
    this.setState({ targetGroupMappings: currentMappings });
  };

  private removeMapping = (index: number) => {
    const currentMappings = this.state.containerMappings;
    currentMappings.splice(index, 1);
    this.props.notifyAngular('containerMappings', currentMappings);
    this.setState({ containerMappings: currentMappings });
  };

  private removeTargetGroupMapping = (index: number) => {
    const currentMappings = this.state.targetGroupMappings;
    currentMappings.splice(index, 1);
    this.props.notifyAngular('targetGroupMappings', currentMappings);
    this.setState({ targetGroupMappings: currentMappings });
  };

  private updateEvaluateTaskDefArtifactFlag = () => {
    this.props.notifyAngular(
      'evaluateTaskDefinitionArtifactExpressions',
      !this.props.command.evaluateTaskDefinitionArtifactExpressions,
    );
    this.setState({
      evaluateTaskDefinitionArtifactExpressions: !this.props.command.evaluateTaskDefinitionArtifactExpressions,
    });
  };

  public render(): React.ReactElement<TaskDefinition> {
    const { command } = this.props;
    const removeMapping = this.removeMapping;
    const removeTargetGroupMapping = this.removeTargetGroupMapping;
    const updateContainerMappingName = this.updateContainerMappingName;
    const updateContainerMappingImage = this.updateContainerMappingImage;
    const updateTargetGroupMappingContainer = this.updateTargetGroupMappingContainer;
    const updateTargetGroupMappingTargetGroup = this.updateTargetGroupMappingTargetGroup;
    const updateTargetGroupMappingPort = this.updateTargetGroupMappingPort;
    const updateEvaluateTaskDefArtifactFlag = this.updateEvaluateTaskDefArtifactFlag;

    const dockerImageOptions = this.state.dockerImages.map(function (image) {
      let msg = '';
      if (image.fromTrigger || image.fromContext) {
        msg = image.fromTrigger ? '(TRIGGER) ' : '(FIND IMAGE RESULT) ';
      }
      return { label: `${msg} (${image.imageId})`, value: image.imageId };
    });

    const targetGroupsAvailable = this.state.targetGroupsAvailable.map(function (targetGroup) {
      return { label: `${targetGroup}`, value: targetGroup };
    });

    const mappingInputs = this.state.containerMappings.map(function (mapping, index) {
      return (
        <tr key={index}>
          <td>
            <input
              data-test-id="Artifacts.containerName"
              className="form-control input-sm"
              required={true}
              placeholder="enter container name..."
              value={mapping.containerName.toString()}
              onChange={(e) => updateContainerMappingName(index, e.target.value)}
            />
          </td>
          <td data-test-id="Artifacts.containerImage">
            <TetheredSelect
              placeholder="Select an image to use..."
              options={dockerImageOptions}
              value={mapping.imageDescription.imageId}
              onChange={(e: Option) => {
                updateContainerMappingImage(index, e as Option<string>);
              }}
              clearable={false}
            />
          </td>
          <td>
            <div className="form-control-static">
              <a
                className="btn-link sm-label"
                data-test-id="Artifacts.containerRemove"
                onClick={() => removeMapping(index)}
              >
                <span className="glyphicon glyphicon-trash" />
                <span className="sr-only">Remove</span>
              </a>
            </div>
          </td>
        </tr>
      );
    });

    const targetGroupInputs = this.state.targetGroupMappings.map(function (mapping, index) {
      return (
        <tr key={index}>
          <td>
            <input
              data-test-id="Artifacts.targetGroupContainer"
              className="form-control input-sm"
              required={true}
              placeholder="Enter a container name ..."
              value={mapping.containerName.toString()}
              onChange={(e) => updateTargetGroupMappingContainer(index, e.target.value)}
            />
          </td>
          <td data-test-id="Artifacts.targetGroup">
            <TetheredSelect
              placeholder="Select a target group to use..."
              options={targetGroupsAvailable}
              value={mapping.targetGroup.toString()}
              onChange={(e: Option) => updateTargetGroupMappingTargetGroup(index, e as Option<string>)}
              clearable={false}
            />
          </td>
          <td>
            <input
              data-test-id="Artifacts.targetGroupPort"
              type="number"
              className="form-control input-sm no-spel"
              required={true}
              value={mapping.containerPort.toString()}
              onChange={(e) => updateTargetGroupMappingPort(index, e.target.valueAsNumber)}
            />
          </td>
          <td>
            <div className="form-control-static">
              <a
                className="btn-link sm-label"
                data-test-id="Artifacts.targetGroupRemove"
                onClick={() => removeTargetGroupMapping(index)}
              >
                <span className="glyphicon glyphicon-trash" />
                <span className="sr-only">Remove</span>
              </a>
            </div>
          </td>
        </tr>
      );
    });

    const newTargetGroupMapping = this.state.targetGroupsAvailable.length ? (
      <button
        className="btn btn-block btn-sm add-new"
        data-test-id="Artifacts.targetGroupAdd"
        onClick={this.pushTargetGroupMapping}
      >
        <span className="glyphicon glyphicon-plus-sign" />
        Add New Target Group Mapping
      </button>
    ) : (
      <div className="sm-label-left">
        <Alert color="warning">No target groups found in the selected account/region/VPC</Alert>
      </div>
    );

    return (
      <div className="container-fluid form-horizontal">
        <div className="form-group">
          <div className="col-md-12">
            <StageArtifactSelectorDelegate
              artifact={this.state.taskDefArtifact.artifact}
              excludedArtifactTypePatterns={this.excludedArtifactTypePatterns}
              expectedArtifactId={this.state.taskDefArtifact.artifactId}
              label="Artifact"
              helpKey="ecs.taskDefinitionArtifact"
              onExpectedArtifactSelected={(artifact: IExpectedArtifact) => this.onExpectedArtifactSelected(artifact.id)}
              onArtifactEdited={this.onArtifactEdited}
              pipeline={command.viewState.pipeline}
              stage={command.viewState.currentStage}
            />
          </div>
        </div>

        <StageConfigField
          groupClassName="form-group evaluateTaskDef"
          label="Expression Evaluation"
          helpKey="ecs.evaluateExpression"
          labelColumns={5}
          fieldColumns={7}
        >
          <CheckboxInput
            checked={command.evaluateTaskDefinitionArtifactExpressions === true}
            onChange={() => {
              updateEvaluateTaskDefArtifactFlag();
            }}
            text="Evaluate SpEL expressions in artifact"
          />
        </StageConfigField>

        <div className="form-group">
          <div className="sm-label-left">
            <b>Container Mappings</b>
            <HelpField id="ecs.containerMappings" />
          </div>
          <form name="ecsTaskDefinitionContainerMappings">
            <table className="table table-condensed packed tags">
              <thead>
                <tr key="header">
                  <th style={{ width: '30%' }}>
                    Container name
                    <HelpField id="ecs.containerMappingName" />
                  </th>
                  <th style={{ width: '70%' }}>
                    Container image
                    <HelpField id="ecs.containerMappingImage" />
                  </th>
                  <th />
                </tr>
              </thead>
              <tbody>{mappingInputs}</tbody>
              <tfoot>
                <tr>
                  <td colSpan={3}>
                    <button
                      className="btn btn-block btn-sm add-new"
                      data-test-id="Artifacts.containerAdd"
                      onClick={this.pushMapping}
                    >
                      <span className="glyphicon glyphicon-plus-sign" />
                      Add New Container Mapping
                    </button>
                  </td>
                </tr>
              </tfoot>
            </table>
          </form>
        </div>
        <div className="form-group">
          <div className="sm-label-left">
            <b>Target Group Mappings</b>
            <HelpField id="ecs.targetGroupMappings" />
          </div>
          <form name="ecsTaskDefinitionTargetGroupMappings">
            <table className="table table-condensed packed tags">
              <thead>
                <tr key="header">
                  <th style={{ width: '30%' }}>
                    Container name
                    <HelpField id="ecs.loadBalancedContainer" />
                  </th>
                  <th style={{ width: '55%' }}>
                    Target group
                    <HelpField id="ecs.loadBalancer.targetGroup" />
                  </th>
                  <th style={{ width: '15%' }}>
                    Target port
                    <HelpField id="ecs.loadbalancing.targetPort" />
                  </th>
                  <th />
                </tr>
              </thead>
              <tbody>{targetGroupInputs}</tbody>
              <tfoot>
                <tr>
                  <td colSpan={4}>{newTargetGroupMapping}</td>
                </tr>
              </tfoot>
            </table>
          </form>
        </div>
      </div>
    );
  }
}

export const TASK_DEFINITION_REACT = 'spinnaker.ecs.serverGroup.configure.wizard.taskDefinition.react';
module(TASK_DEFINITION_REACT, []).component(
  'taskDefinitionReact',
  react2angular(withErrorBoundary(TaskDefinition, 'taskDefinitionReact'), [
    'command',
    'notifyAngular',
    'configureCommand',
  ]),
);
