import React from 'react';

import { useDeckRuntimeServices } from '../../../bootstrap/DeckRuntimeContext';
import { HelpField } from '../../../help';
import type { IInstanceTypeCategory, IPreferredInstanceType } from '../../../instance';
import type { IServerGroupCommand } from './serverGroupCommandBuilder.service';

export interface IInstanceTypeSelectorProps {
  command: IServerGroupCommand;
  onTypeChanged: (type: string) => void;
}

export function InstanceTypeSelector(props: IInstanceTypeSelectorProps) {
  const { instanceTypeService } = useDeckRuntimeServices();
  const { command, onTypeChanged } = props;
  const [selectedInstanceProfile, setSelectedInstanceProfile] = React.useState<IInstanceTypeCategory | null>(null);
  const previousInstanceProfile = React.useRef<string>();
  const previousInstanceTypes = React.useRef<string[]>();
  const previousVirtualizationType = React.useRef<string>();

  const updateFamilies = () => {
    const availableTypes =
      command.backingData && command.backingData.filtered ? command.backingData.filtered.instanceTypes || [] : [];
    instanceTypeService.getCategories(command.selectedProvider).then((categories) => {
      const profile = categories.find((category) => category.type === command.viewState.instanceProfile);
      if (profile && !command.viewState.disableImageSelection) {
        setSelectedInstanceProfile({
          ...profile,
          families: profile.families.map((family) => ({
            ...family,
            instanceTypes: family.instanceTypes.map((instanceType) => ({
              ...instanceType,
              unavailable: availableTypes.every((available: string) => available !== instanceType.name),
            })),
          })),
        });
      } else {
        setSelectedInstanceProfile(profile || null);
      }
    });
  };

  React.useEffect(() => {
    const instanceTypes =
      command.backingData && command.backingData.filtered && command.backingData.filtered.instanceTypes;
    if (
      previousInstanceProfile.current !== command.viewState.instanceProfile ||
      previousVirtualizationType.current !== command.virtualizationType ||
      previousInstanceTypes.current !== instanceTypes
    ) {
      previousInstanceProfile.current = command.viewState.instanceProfile;
      previousVirtualizationType.current = command.virtualizationType;
      previousInstanceTypes.current = instanceTypes;
      updateFamilies();
    }
  });

  const selectInstanceType = (type: IPreferredInstanceType) => {
    if (type.unavailable) {
      return;
    }
    command.instanceType = type.name;
    if (command.viewState.dirty && command.viewState.dirty.instanceType) {
      delete command.viewState.dirty.instanceType;
    }
    instanceTypeService.getInstanceTypeDetails(command.selectedProvider, type.name).then((instanceTypeDetails) => {
      command.viewState.instanceTypeDetails = instanceTypeDetails;
    });
    onTypeChanged && onTypeChanged(type.name);
  };

  const getStorageDescription = (instanceType: IPreferredInstanceType) => {
    return command.instanceType === instanceType.name && command.viewState.overriddenStorageDescription
      ? command.viewState.overriddenStorageDescription
      : `${instanceType.storage.count}x${instanceType.storage.size}`;
  };

  if (!selectedInstanceProfile) {
    return null;
  }

  return (
    <>
      <DirtyInstanceTypeWarning command={command} />
      {selectedInstanceProfile.families.map((family) => (
        <div className="row" key={family.type}>
          <div className="col-md-12">
            <h4>
              {selectedInstanceProfile.label}: {family.type}
            </h4>
            <p>{family.description}</p>
            <table className="table table-hover">
              <thead>
                <tr>
                  <th />
                  <th>Size</th>
                  <th>vCPU</th>
                  <th>Mem (GiB)</th>
                  <th>{family.storageType || 'SSD'} Storage (GB)</th>
                  <th>Cost</th>
                </tr>
              </thead>
              <tbody>
                {family.instanceTypes.map((instanceType) => (
                  <tr
                    className={`instance-type-row ${command.instanceType === instanceType.name ? 'info' : ''} ${
                      instanceType.unavailable ? 'unavailable' : 'clickable'
                    }`}
                    key={instanceType.name}
                    onClick={() => selectInstanceType(instanceType)}
                  >
                    <td>
                      {instanceType.unavailable && <span className="unavailable-marker">Unavailable </span>}
                      <input
                        checked={command.instanceType === instanceType.name}
                        disabled={instanceType.unavailable}
                        readOnly={true}
                        type="radio"
                      />
                    </td>
                    <td>{instanceType.label}</td>
                    <td>{instanceType.cpu}</td>
                    <td>{instanceType.memory}</td>
                    <td>
                      {instanceType.storage.type === 'EBS' ? 'EBS Only' : getStorageDescription(instanceType)}
                      {command.instanceType === instanceType.name && command.viewState.overriddenStorageDescription && (
                        <span className="storage-override-indicator">
                          {' '}
                          <HelpField id="instanceType.storageOverridden" />
                        </span>
                      )}
                    </td>
                    <td>
                      <CostFactor factor={instanceType.costFactor} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ))}
    </>
  );
}

export function DirtyInstanceTypeWarning({ command }: { command: IServerGroupCommand }) {
  const [, forceRender] = React.useReducer((count) => count + 1, 0);
  const dirtyInstanceType = command.viewState.dirty && command.viewState.dirty.instanceType;
  if (!dirtyInstanceType) {
    return null;
  }

  return (
    <div className="row dirty-instance-type-warning">
      <div className="col-md-12">
        <div className="alert alert-warning">
          <p>
            <i className="fa fa-exclamation-triangle" /> The previously selected instance type ({dirtyInstanceType})
            could not be deployed with the selected image or in the selected location.
          </p>
          <p className="text-right">
            <button
              className="btn btn-sm btn-default dirty-flag-dismiss"
              onClick={() => {
                command.viewState.dirty.instanceType = null;
                forceRender();
              }}
              type="button"
            >
              Okay
            </button>
          </p>
        </div>
      </div>
    </div>
  );
}

function CostFactor({ factor }: { factor: number }) {
  if (!factor) {
    return null;
  }
  return <span className="cost-factor">{'$'.repeat(factor)}</span>;
}
