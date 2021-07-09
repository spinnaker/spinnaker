import * as React from 'react';

import { REST } from '../../../api';
import { IDeploymentStrategyAdditionalFieldsProps } from '../../deploymentStrategy.registry';
import { IServerGroup } from '../../../domain';
import { HealthCounts } from '../../../healthCounts';
import { NameUtils } from '../../../naming';
import { useData } from '../../../presentation';
import { Spinner } from '../../../widgets';

export function HighlanderPreview(props: IDeploymentStrategyAdditionalFieldsProps) {
  const isPipeline = props.command.viewState.mode === 'editPipeline';
  return isPipeline ? null : <HighlanderPreviewCmp {...props} />;
}

export function HighlanderPreviewCmp(props: IDeploymentStrategyAdditionalFieldsProps) {
  const { credentials, region, application, stack, freeFormDetails } = props.command;
  const cluster = NameUtils.getClusterName(application, stack, freeFormDetails);
  const fetchit = useData(() => REST('/applications').path(application, 'serverGroups').get<IServerGroup[]>(), [], []);
  const { backingData, ...command } = props.command;
  const { capacity } = command;

  const serverGroups = fetchit.result.filter((sg) => {
    return sg.cluster === cluster && sg.account === credentials && sg.region === region;
  });

  if (fetchit.status === 'PENDING') {
    return <Spinner />;
  }

  if (fetchit.status === 'REJECTED') {
    return <pre>{fetchit.error}</pre>;
  }

  const currentUpCount = serverGroups.reduce((total, sg) => total + sg.instanceCounts.up, 0);

  const pluralize = (str: string, val: number | string) => (val === 1 ? str : str + 's');
  const containStr = serverGroups.length === 1 ? 'contains' : 'contain';
  const serverGroupStr = pluralize('server group', serverGroups.length);
  const instanceStr = pluralize('instance', currentUpCount);

  return (
    <div className="flex-container-v margin-between-md">
      <div>
        Highlander will create a new server group with {capacity.desired} {pluralize('instance', capacity.desired)}.
      </div>
      <div>
        Then it will destroy the following {serverGroups.length} {serverGroupStr} which currently {containStr}{' '}
        {currentUpCount} healthy {instanceStr}:
      </div>

      <ul>
        {serverGroups.map((sg) => (
          <li key={sg.name} className="flex-container-h margin-between-lg">
            <span className="bold">{sg.name}</span> <HealthCounts container={sg.instanceCounts} />
          </li>
        ))}
      </ul>
    </div>
  );
}
