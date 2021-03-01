import classNames from 'classnames';
import React, { useState } from 'react';

import { Icon } from '@spinnaker/presentation';

import { EnvironmentBadge } from '../EnvironmentBadge';
import { StatusBubble } from '../StatusBubble';
import { IManagedEnvironmentSummary, IManagedResourceSummary } from '../../domain';
import { useEnvironmentTypeFromResources } from '../useEnvironmentTypeFromResources.hooks';

import './EnvironmentRow.less';

interface IEnvironmentRowProps {
  name: string;
  resources?: IManagedResourceSummary[];
  pinnedVersions?: IManagedEnvironmentSummary['artifacts'];
  children?: React.ReactNode;
}

export function EnvironmentRow({ name, resources = [], pinnedVersions, children }: IEnvironmentRowProps) {
  const [isCollapsed, setIsCollapsed] = useState(false);
  const isCritical = useEnvironmentTypeFromResources(resources);

  const envRowClasses = classNames({
    srow: true,
  });

  return (
    <div className="EnvironmentRow">
      <div className={envRowClasses}>
        <span className="clickableArea">
          <div className="titleColumn flex-container-h left middle sp-margin-s-right">
            <EnvironmentBadge name={name} critical={isCritical} />
          </div>
          <div className="flex-container-h flex-grow flex-pull-right">
            {pinnedVersions && pinnedVersions?.length > 0 ? (
              <StatusBubble
                iconName="pin"
                appearance="warning"
                size="small"
                quantity={pinnedVersions.length > 1 ? pinnedVersions.length : undefined}
              />
            ) : null}
          </div>
          <div className="expand" onClick={() => setIsCollapsed(!isCollapsed)}>
            <Icon name={isCollapsed ? 'accordionExpand' : 'accordionCollapse'} size="extraSmall" />
          </div>
        </span>
      </div>

      {!isCollapsed && <div style={{ margin: '16px 0 40px 8px' }}>{children}</div>}
    </div>
  );
}
