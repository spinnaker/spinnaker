import { isEqual } from 'lodash';
import React from 'react';
import { Alert } from 'react-bootstrap';
import type { Option } from 'react-select';

import { HelpField, TetheredSelect } from '@spinnaker/core';

import type {
  IEcsServerGroupCommand,
  IEcsServiceDiscoveryRegistryAssociation,
} from '../../serverGroupConfiguration.service';

export interface IServiceDiscoveryProps {
  command: IEcsServerGroupCommand;
  notifyAngular: (key: string, value: any) => void;
  configureCommand: (query: string) => PromiseLike<void>;
}

export interface IEcsServiceDiscoveryRegistry {
  account: string;
  region: string;
  name: string;
  id: string;
  arn: string;
  displayName: string;
}

interface IServiceDiscoveryState {
  serviceDiscoveryAssociations: IEcsServiceDiscoveryRegistryAssociation[];
  serviceDiscoveryRegistriesAvailable: IEcsServiceDiscoveryRegistry[];
  useTaskDefinitionArtifact: boolean;
}

export class ServiceDiscovery extends React.Component<IServiceDiscoveryProps, IServiceDiscoveryState> {
  constructor(props: IServiceDiscoveryProps) {
    super(props);
    const cmd = this.props.command;

    const serviceDiscoveryAssociations = this.normalizeContainerNames(
      cmd.serviceDiscoveryAssociations || [],
      cmd.useTaskDefinitionArtifact,
    );
    cmd.serviceDiscoveryAssociations = serviceDiscoveryAssociations;
    this.state = {
      serviceDiscoveryAssociations,
      serviceDiscoveryRegistriesAvailable: this.mergeRegistries(
        cmd.backingData?.filtered?.serviceDiscoveryRegistries || [],
        serviceDiscoveryAssociations,
      ),
      useTaskDefinitionArtifact: cmd.useTaskDefinitionArtifact,
    };
  }

  public componentDidMount() {
    this.props.configureCommand('1').then(() => {
      this.setState({
        serviceDiscoveryRegistriesAvailable: this.mergeRegistries(
          this.props.command.backingData.filtered.serviceDiscoveryRegistries || [],
          this.state.serviceDiscoveryAssociations,
        ),
      });
    });
  }

  public componentDidUpdate() {
    const cmd = this.props.command;
    const currentAssociations = cmd.serviceDiscoveryAssociations || [];
    const serviceDiscoveryAssociations = this.normalizeContainerNames(
      currentAssociations,
      cmd.useTaskDefinitionArtifact,
    );
    if (!isEqual(currentAssociations, serviceDiscoveryAssociations)) {
      cmd.serviceDiscoveryAssociations = serviceDiscoveryAssociations;
      this.props.notifyAngular('serviceDiscoveryAssociations', serviceDiscoveryAssociations);
    }
    const nextState: IServiceDiscoveryState = {
      serviceDiscoveryAssociations,
      serviceDiscoveryRegistriesAvailable: this.mergeRegistries(
        cmd.backingData?.filtered?.serviceDiscoveryRegistries || [],
        serviceDiscoveryAssociations,
      ),
      useTaskDefinitionArtifact: cmd.useTaskDefinitionArtifact,
    };
    if (!isEqual(this.state, nextState)) {
      this.setState(nextState);
    }
  }

  private normalizeContainerNames = (
    associations: IEcsServiceDiscoveryRegistryAssociation[],
    useTaskDefinitionArtifact: boolean,
  ): IEcsServiceDiscoveryRegistryAssociation[] => {
    const normalized = associations.map((association) => ({
      ...association,
      containerName: useTaskDefinitionArtifact ? association.containerName || '' : null,
    }));
    return isEqual(associations, normalized) ? associations : normalized;
  };

  private getNameToRegistryMap = (): Map<string, IEcsServiceDiscoveryRegistry> => {
    const displayNameToRegistry = new Map<string, IEcsServiceDiscoveryRegistry>();
    this.state.serviceDiscoveryRegistriesAvailable.forEach((e) => {
      displayNameToRegistry.set(e.displayName, e);
    });

    return displayNameToRegistry;
  };

  private mergeRegistries = (
    available: IEcsServiceDiscoveryRegistry[],
    associations: IEcsServiceDiscoveryRegistryAssociation[],
  ): IEcsServiceDiscoveryRegistry[] => {
    const registries = new Map<string, IEcsServiceDiscoveryRegistry>();
    [...available, ...associations.map((association) => association.registry)].filter(Boolean).forEach((registry) => {
      registries.set(registry.displayName, registry as IEcsServiceDiscoveryRegistry);
    });
    return Array.from(registries.values());
  };

  private getEmptyRegistry = (): IEcsServiceDiscoveryRegistry => {
    return {
      account: '',
      region: '',
      name: '',
      id: '',
      arn: '',
      displayName: '',
    };
  };

  private updateServiceDiscoveryRegistry = (index: number, newRegistry: Option<string>) => {
    const registryMap = this.getNameToRegistryMap();
    let newServiceDiscoveryRegistry = registryMap.get(newRegistry.value);

    if (!newServiceDiscoveryRegistry) {
      newServiceDiscoveryRegistry = this.getEmptyRegistry();
    }

    const currentAssociations = this.state.serviceDiscoveryAssociations;
    const targetAssociation = currentAssociations[index];
    targetAssociation.registry = newServiceDiscoveryRegistry;
    this.props.notifyAngular('serviceDiscoveryAssociations', currentAssociations);
    this.setState({ serviceDiscoveryAssociations: currentAssociations });
  };

  private updateServiceDiscoveryPort = (index: number, targetPort: number) => {
    const currentAssociations = this.state.serviceDiscoveryAssociations;
    const targetAssociations = currentAssociations[index];
    targetAssociations.containerPort = targetPort;
    this.props.notifyAngular('serviceDiscoveryAssociations', currentAssociations);
    this.setState({ serviceDiscoveryAssociations: currentAssociations });
  };

  private updateServiceDiscoveryContainerName = (index: number, targetContainerName: string) => {
    const currentAssociations = this.state.serviceDiscoveryAssociations;
    const targetAssociations = currentAssociations[index];
    targetAssociations.containerName = targetContainerName;
    this.props.notifyAngular('serviceDiscoveryAssociations', currentAssociations);
    this.setState({ serviceDiscoveryAssociations: currentAssociations });
  };

  private pushServiceDiscoveryAssociation = () => {
    const registryAssociations = this.state.serviceDiscoveryAssociations;
    registryAssociations.push({ registry: this.getEmptyRegistry(), containerPort: 80, containerName: '' });
    this.setState({ serviceDiscoveryAssociations: registryAssociations });
  };

  private removeServiceDiscoveryAssociations = (index: number) => {
    const currentAssociations = this.state.serviceDiscoveryAssociations;
    currentAssociations.splice(index, 1);
    this.props.notifyAngular('serviceDiscoveryAssociations', currentAssociations);
    this.setState({ serviceDiscoveryAssociations: currentAssociations });
  };

  public render(): React.ReactElement<ServiceDiscovery> {
    const removeServiceDiscoveryAssociation = this.removeServiceDiscoveryAssociations;
    const updateServiceDiscoveryRegistry = this.updateServiceDiscoveryRegistry;
    const updateServiceDiscoveryPort = this.updateServiceDiscoveryPort;
    const updateServiceDiscoveryContainerName = this.updateServiceDiscoveryContainerName;

    const registriesAvailable = this.state.serviceDiscoveryRegistriesAvailable.map(function (registry) {
      return { label: `${registry.displayName}`, value: registry.displayName };
    });

    const useTaskDefinitionArtifact = this.state.useTaskDefinitionArtifact;
    const serviceDiscoveryInputs = this.state.serviceDiscoveryAssociations.map(function (mapping, index) {
      return (
        <tr key={index}>
          {useTaskDefinitionArtifact && (
            <td>
              <input
                aria-label={`Container name ${index + 1}`}
                className="form-control input-sm"
                data-test-id="ServiceDiscovery.containerName"
                placeholder="Enter a container name ..."
                required={true}
                value={mapping.containerName}
                onChange={(e) => updateServiceDiscoveryContainerName(index, e.target.value)}
              />
            </td>
          )}
          <td data-test-id="ServiceDiscovery.registry">
            <TetheredSelect
              inputProps={{ 'aria-label': `Service registry ${index + 1}` }}
              placeholder="Select a registry..."
              options={registriesAvailable}
              value={mapping.registry.displayName.toString()}
              onChange={(e: Option) => updateServiceDiscoveryRegistry(index, e as Option<string>)}
              clearable={false}
              required={true}
            />
          </td>
          <td>
            <input
              aria-label={`Container port ${index + 1}`}
              data-test-id="ServiceDiscovery.containerPort"
              type="number"
              className="form-control input-sm no-spel"
              required={true}
              value={mapping.containerPort}
              onChange={(e) => updateServiceDiscoveryPort(index, e.target.valueAsNumber)}
            />
          </td>
          <td>
            <div className="form-control-static">
              <button
                aria-label={`Remove service registry ${index + 1}`}
                className="btn-link sm-label"
                onClick={() => removeServiceDiscoveryAssociation(index)}
                type="button"
              >
                <span className="glyphicon glyphicon-trash" />
                <span className="sr-only">Remove</span>
              </button>
            </div>
          </td>
        </tr>
      );
    });

    const newServiceDiscoveryAssociation = this.state.serviceDiscoveryRegistriesAvailable.length ? (
      <button className="btn btn-block btn-sm add-new" onClick={this.pushServiceDiscoveryAssociation} type="button">
        <span className="glyphicon glyphicon-plus-sign" />
        Add New Service Registry
      </button>
    ) : (
      <div className="sm-label-left">
        <Alert color="warning">No registries found in the selected account/region/VPC</Alert>
      </div>
    );

    return (
      <div className="container-fluid form-horizontal">
        <div className="form-group">
          <div className="sm-label-left">
            <b>Service Registries (optional)</b>
            <HelpField id="ecs.serviceDiscovery" />
          </div>
          <form name="ecsServiceDiscoveryRegistryMappings">
            <table className="table table-condensed packed tags">
              <thead>
                <tr>
                  {useTaskDefinitionArtifact && (
                    <th style={{ width: '30%' }}>
                      Container name
                      <HelpField id="ecs.serviceDiscoveryContainerName" />
                    </th>
                  )}
                  {useTaskDefinitionArtifact ? (
                    <th style={{ width: '55%' }}>
                      Registry
                      <HelpField id="ecs.serviceDiscoveryRegistry" />
                    </th>
                  ) : (
                    <th style={{ width: '80%' }}>
                      Registry
                      <HelpField id="ecs.serviceDiscoveryRegistry" />
                    </th>
                  )}
                  <th style={{ width: '15%' }}>
                    Port
                    <HelpField id="ecs.serviceDiscoveryContainerPort" />
                  </th>
                  <th />
                </tr>
              </thead>
              <tbody>{serviceDiscoveryInputs}</tbody>
              <tfoot>
                <tr>
                  <td colSpan={4}>{newServiceDiscoveryAssociation}</td>
                </tr>
              </tfoot>
            </table>
          </form>
        </div>
      </div>
    );
  }
}
