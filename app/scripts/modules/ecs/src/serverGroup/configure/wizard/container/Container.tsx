import { module } from 'angular';
import { isEqual, uniqWith } from 'lodash';
import * as React from 'react';
import { Alert } from 'react-bootstrap';
import { Option } from 'react-select';
import { react2angular } from 'react2angular';

import { HelpField, TetheredSelect, withErrorBoundary } from '@spinnaker/core';

import {
  IEcsDockerImage,
  IEcsServerGroupCommand,
  IEcsTargetGroupMapping,
} from '../../serverGroupConfiguration.service';

export interface IContainerProps {
  command: IEcsServerGroupCommand;
  notifyAngular: (key: string, value: any) => void;
  configureCommand: (query: string) => PromiseLike<void>;
}

interface IContainerState {
  imageDescription: IEcsDockerImage;
  computeUnits: number;
  reservedMemory: number;
  dockerImages: IEcsDockerImage[];
  targetGroupsAvailable: string[];
  targetGroupMappings: IEcsTargetGroupMapping[];
}

export class Container extends React.Component<IContainerProps, IContainerState> {
  constructor(props: IContainerProps) {
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
    cmd.containerMappings = null;

    this.state = {
      imageDescription: cmd.imageDescription ? cmd.imageDescription : this.getEmptyImageDescription(),
      computeUnits: cmd.computeUnits,
      reservedMemory: cmd.reservedMemory,
      dockerImages: cmd.backingData && cmd.backingData.filtered ? cmd.backingData.filtered.images : [],
      targetGroupMappings: cmd.targetGroupMappings,
      targetGroupsAvailable: cmd.backingData && cmd.backingData.filtered ? cmd.backingData.filtered.targetGroups : [],
    };

    this.state.targetGroupMappings.forEach((targetGroupMapping) => {
      targetGroupMapping.containerName = '';
    });
  }

  public componentDidMount() {
    this.props.configureCommand('1').then(() => {
      this.setState({
        dockerImages: this.props.command.backingData.filtered.images,
        targetGroupsAvailable: this.props.command.backingData.filtered.targetGroups,
      });
    });
  }

  // TODO: Separate docker image component used by both TaskDefinition and Container

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

  private pushTargetGroupMapping = () => {
    const targetMaps = this.state.targetGroupMappings;
    targetMaps.push({ containerName: '', targetGroup: '', containerPort: 80 });
    this.setState({ targetGroupMappings: targetMaps });
  };

  private updateContainerMappingImage = (newImage: Option<string>) => {
    const imageMap = this.getIdToImageMap();
    let newImageDescription = imageMap.get(newImage.value);
    if (!newImageDescription) {
      newImageDescription = this.getEmptyImageDescription();
    }

    this.props.notifyAngular('imageDescription', newImageDescription);
    this.setState({ imageDescription: newImageDescription });
  };

  private updateComputeUnits = (newComputeUnits: number) => {
    this.props.notifyAngular('computeUnits', newComputeUnits);
    this.setState({ computeUnits: newComputeUnits });
  };

  private updateReservedMemory = (newReservedMemory: number) => {
    this.props.notifyAngular('reservedMemory', newReservedMemory);
    this.setState({ reservedMemory: newReservedMemory });
  };

  private updateTargetGroupMappingTargetGroup = (index: number, newTargetGroup: Option<string>) => {
    const currentMappings = this.state.targetGroupMappings;
    const targetMapping = currentMappings[index];
    targetMapping.targetGroup = newTargetGroup.value;
    this.props.notifyAngular('targetGroupMappings', currentMappings);
    this.setState({ targetGroupMappings: currentMappings });
    this.updateDirtyTargetGroups();
  };

  private updateTargetGroupMappingPort = (index: number, targetPort: number) => {
    const currentMappings = this.state.targetGroupMappings;
    const targetMapping = currentMappings[index];
    targetMapping.containerPort = targetPort;
    this.props.notifyAngular('targetGroupMappings', currentMappings);
    this.setState({ targetGroupMappings: currentMappings });
  };

  private removeTargetGroupMapping = (index: number) => {
    const currentMappings = this.state.targetGroupMappings;
    currentMappings.splice(index, 1);
    this.props.notifyAngular('targetGroupMappings', currentMappings);
    this.setState({ targetGroupMappings: currentMappings });
  };

  private updateDirtyTargetGroups = () => {
    this.props.command.viewState.dirty.targetGroups = [];
  };

  public render(): React.ReactElement<Container> {
    const removeTargetGroupMapping = this.removeTargetGroupMapping;
    const updateContainerMappingImage = this.updateContainerMappingImage;
    const updateTargetGroupMappingTargetGroup = this.updateTargetGroupMappingTargetGroup;
    const updateTargetGroupMappingPort = this.updateTargetGroupMappingPort;
    const updateComputeUnits = this.updateComputeUnits;
    const updateReservedMemory = this.updateReservedMemory;
    const dirtyTagetGroups =
      this.props.command.viewState.dirty && this.props.command.viewState.dirty.targetGroups
        ? this.props.command.viewState.dirty.targetGroups
        : [];

    const dockerImageOptions = this.state.dockerImages.map(function (image) {
      let msg = '';
      if (image.fromTrigger || image.fromContext) {
        msg = image.fromTrigger ? '(TRIGGER) ' : '(FIND IMAGE RESULT) ';
      }
      return { label: `${msg} (${image.imageId})`, value: image.imageId };
    });

    const dirtyTargetGroupList = dirtyTagetGroups
      ? dirtyTagetGroups.map(function (targetGroup, index) {
          return <li key={index}>{targetGroup}</li>;
        })
      : '';

    const dirtyTargetGroupSection = (
      <div className="alert alert-warning">
        <p>
          <i className="fa fa-exclamation-triangle"></i>
          The following target groups could not be found in the selected account/region/VPC and were removed:
        </p>
        <ul>{dirtyTargetGroupList}</ul>
        <br />
        <p className="text-left">Please select the target group(s) from the dropdown to resolve this error.</p>
      </div>
    );

    const newTargetGroupMapping = this.state.targetGroupsAvailable.length ? (
      <button
        className="btn btn-block btn-sm add-new"
        data-test-id="ContainerInputs.targetGroupAdd"
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

    const targetGroupsAvailable = this.state.targetGroupsAvailable.map(function (targetGroup) {
      return { label: `${targetGroup}`, value: targetGroup };
    });

    const targetGroupInputs = this.state.targetGroupMappings.map(function (mapping, index) {
      return (
        <tr key={index}>
          <td data-test-id="ContainerInputs.targetGroup">
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
              data-test-id="ContainerInputs.targetGroupPort"
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
                data-test-id="ContainerInputs.targetGroupRemove"
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

    return (
      <div className="container-fluid form-horizontal">
        {dirtyTagetGroups.length > 0 ? <div>{dirtyTargetGroupSection}</div> : ''}
        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            <b>Container Image</b>
            <HelpField id="ecs.containerMappingImage" />
          </div>
          <div className="col-md-9" data-test-id="ContainerInputs.containerImage">
            <TetheredSelect
              placeholder="Select an image to use..."
              options={dockerImageOptions}
              value={this.state.imageDescription.imageId}
              onChange={(e: Option) => {
                updateContainerMappingImage(e as Option<string>);
              }}
              clearable={false}
            />
          </div>
        </div>
        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            <b>Compute Units</b>
            <HelpField id="ecs.capacity.reserved.computeUnits" />
          </div>
          <div className="col-md-9" style={{ width: '100px' }}>
            <input
              data-test-id="ContainerInputs.computeUnits"
              type="number"
              className="form-control input-sm no-spel"
              required={false}
              value={this.state.computeUnits}
              onChange={(e) => updateComputeUnits(e.target.valueAsNumber)}
            />
          </div>
        </div>
        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            <b>Reserved Memory</b>
            <HelpField id="ecs.capacity.reserved.memory" />
          </div>
          <div className="col-md-9" style={{ width: '100px' }}>
            <input
              data-test-id="ContainerInputs.reservedMemory"
              type="number"
              className="form-control input-sm no-spel"
              required={false}
              value={this.state.reservedMemory}
              onChange={(e) => updateReservedMemory(e.target.valueAsNumber)}
            />
          </div>
        </div>
        <div className="form-group">
          <div className="sm-label-left">
            <b>Target Group Mappings</b>
            <HelpField id="ecs.targetGroupMappings" />
          </div>
          <form name="ecsContainerTargetGroupMappings">
            <table className="table table-condensed packed tags">
              <thead>
                <tr key="header">
                  <th style={{ width: '80%' }}>
                    Target group
                    <HelpField id="ecs.loadBalancer.targetGroup" />
                  </th>
                  <th style={{ width: '20%' }}>
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

export const CONTAINER_REACT = 'spinnaker.ecs.serverGroup.configure.wizard.container.react';
module(CONTAINER_REACT, []).component(
  'containerReact',
  react2angular(withErrorBoundary(Container, 'containerReact'), ['command', 'notifyAngular', 'configureCommand']),
);
