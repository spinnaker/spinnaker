import React from 'react';

import { DirtyInstanceTypeWarning } from './InstanceTypeSelector';
import { useDeckRuntimeServices } from '../../../bootstrap/DeckRuntimeContext';
import { CloudProviderRegistry } from '../../../cloudProvider';
import type { IInstanceTypeCategory } from '../../../instance';
import { ModalWizard } from '../../../modal/wizard/ModalWizard';
import type { IServerGroupCommand } from './serverGroupCommandBuilder.service';

export interface ICustomInstanceBuilderProps {
  command: IServerGroupCommand;
  onTypeChanged: (type: string) => void;
}

export interface IInstanceArchetypeSelectorProps {
  command: IServerGroupCommand;
  onProfileChanged: (profile: string | null) => void;
  onTypeChanged: (type: string) => void;
}

export function InstanceArchetypeSelector(props: IInstanceArchetypeSelectorProps) {
  const { instanceTypeService } = useDeckRuntimeServices();
  const { command, onProfileChanged, onTypeChanged } = props;
  const [instanceProfiles, setInstanceProfiles] = React.useState<IInstanceTypeCategory[]>([]);
  const [selectedInstanceProfile, setSelectedInstanceProfile] = React.useState<IInstanceTypeCategory | null>(null);

  const updateInstanceTypeWizardState = () => {
    if (ModalWizard.renderedPages.length > 0 && ModalWizard.getPage('instance-type')) {
      if (command.instanceType) {
        ModalWizard.markComplete('instance-type');
      } else {
        ModalWizard.markIncomplete('instance-type');
      }
    }
  };

  const selectInstanceType = (type: string, profiles = instanceProfiles, allowToggle = true) => {
    const nextType = allowToggle && selectedInstanceProfile && selectedInstanceProfile.type === type ? null : type;
    const nextProfile = profiles.find((profile) => profile.type === nextType) || null;
    const profileChanged = command.viewState.instanceProfile !== nextType;
    command.viewState.instanceProfile = nextType;
    if (command.instanceType && nextProfile && !['custom', 'buildCustom'].includes(nextProfile.type)) {
      const found = nextProfile.families.some((family) =>
        family.instanceTypes.some(
          (instanceType) => instanceType.name === command.instanceType && !instanceType.unavailable,
        ),
      );
      if (!found) {
        command.instanceType = null;
      }
    }
    updateInstanceTypeWizardState();
    setSelectedInstanceProfile(nextProfile);
    if (profileChanged) {
      onProfileChanged && onProfileChanged(nextType);
    }
  };

  React.useEffect(() => {
    instanceTypeService.getCategories(command.selectedProvider).then((categories) => {
      setInstanceProfiles(categories);
      if (command.region && command.instanceType && !command.viewState.instanceProfile) {
        selectInstanceType('custom', categories, false);
      } else {
        selectInstanceType(command.viewState.instanceProfile, categories, false);
      }
    });
  }, [command]);

  const updateInstanceTypeDetails = () => {
    instanceTypeService
      .getInstanceTypeDetails(command.selectedProvider, command.instanceType)
      .then((instanceTypeDetails) => {
        command.viewState.instanceTypeDetails = instanceTypeDetails;
      });
    onTypeChanged && onTypeChanged(command.instanceType);
  };

  const updateBuildCustomInstanceType = (type: string) => {
    updateInstanceTypeWizardState();
    onTypeChanged && onTypeChanged(type);
  };

  const CustomInstanceBuilder = CloudProviderRegistry.getValue(
    command.cloudProvider || command.selectedProvider,
    'instance.CustomInstanceBuilder',
  ) as React.ComponentType<ICustomInstanceBuilderProps>;

  const columns =
    instanceProfiles.length % 5 === 0 || instanceProfiles.length === 7 ? 5 : instanceProfiles.length % 4 === 0 ? 4 : 3;
  const archetypeColumnClass = `archetype-columns archetype-columns-${columns}`;

  return (
    <div className="instance-archetype-selector">
      <div className="row">
        <h4 className="col-md-12">My application is:</h4>
      </div>
      {instanceProfiles.map((instanceProfile) => (
        <div className={archetypeColumnClass} key={instanceProfile.type}>
          <button
            className={`instance-profile ${command.viewState.instanceProfile === instanceProfile.type ? 'active' : ''}`}
            onClick={() => selectInstanceType(instanceProfile.type)}
            type="button"
          >
            {command.viewState.instanceProfile === instanceProfile.type && (
              <span className="far fa-check-circle selected-indicator" />
            )}
            <div className="panel-heading">
              <h4>
                <span className={`glyphicon glyphicon-${instanceProfile.icon}`} />
                <div>{instanceProfile.label}</div>
              </h4>
            </div>
          </button>
        </div>
      ))}
      {selectedInstanceProfile && selectedInstanceProfile.description && (
        <div className="row fade-in">
          <div className="col-md-12">
            <p>{selectedInstanceProfile.description}</p>
            {selectedInstanceProfile.type !== 'custom' && selectedInstanceProfile.stats && (
              <dl className="dl-horizontal">
                <dt>{selectedInstanceProfile.stats.families.length === 1 ? 'Family' : 'Families'}</dt>
                <dd>{selectedInstanceProfile.stats.families.join(', ')}</dd>
                <dt>CPUs</dt>
                <dd>
                  {selectedInstanceProfile.stats.cpu.min} &ndash; {selectedInstanceProfile.stats.cpu.max}
                </dd>
                <dt>Memory (GB)</dt>
                <dd>
                  {selectedInstanceProfile.stats.memory.min} &ndash; {selectedInstanceProfile.stats.memory.max}
                </dd>
                <dt>Storage (GB)</dt>
                <dd>
                  {selectedInstanceProfile.stats.storage.min} &ndash; {selectedInstanceProfile.stats.storage.max}
                </dd>
                <dt>Cost</dt>
                <dd>{renderCostFactor(selectedInstanceProfile.stats.costFactor)}</dd>
              </dl>
            )}
          </div>
        </div>
      )}
      {command.viewState.instanceProfile === 'custom' && (
        <div className="row fade-in">
          <div className="col-md-12">
            <DirtyInstanceTypeWarning command={command} />
            <select
              className="form-control input-sm custom-instance-type"
              onChange={(event) => {
                command.instanceType = event.target.value;
                updateInstanceTypeDetails();
                updateInstanceTypeWizardState();
              }}
              required={true}
              value={command.instanceType || ''}
            >
              <option value="">Select an instance type...</option>
              {(command.backingData.filtered.instanceTypes || []).map((customType: string) => (
                <option key={customType} value={customType}>
                  {customType}
                </option>
              ))}
            </select>
          </div>
        </div>
      )}
      {command.viewState.instanceProfile === 'buildCustom' && CustomInstanceBuilder && (
        <CustomInstanceBuilder command={command} onTypeChanged={updateBuildCustomInstanceType} />
      )}
    </div>
  );
}

function renderCostFactor(factor?: number) {
  return factor ? '$'.repeat(factor) : null;
}
