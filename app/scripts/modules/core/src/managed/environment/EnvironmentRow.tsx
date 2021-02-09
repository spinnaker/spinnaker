import React, { useState } from 'react';
import classNames from 'classnames';

import { IManagedResourceSummary, IManagedEnvironmentSummary } from '../../domain';
import { Icon } from '../../presentation';

import { StatusBubble } from '../StatusBubble';

import { EnvironmentBadge } from '../EnvironmentBadge';
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
            {pinnedVersions?.length > 0 ? (
              <StatusBubble
                iconName="pin"
                appearance="warning"
                size="small"
                quantity={pinnedVersions.length > 1 ? pinnedVersions.length : null}
              />
            ) : null}
          </div>
          <div className="expand" onClick={() => setIsCollapsed(!isCollapsed)}>
            {isCollapsed && <Icon name="accordionExpand" size="extraSmall" />}
            {!isCollapsed && <Icon name="accordionCollapse" size="extraSmall" />}
          </div>
        </span>
        {/* <div className="select">
            <i className={`ico icon-checkbox-unchecked`}/>
          </div> */}
      </div>

      {!isCollapsed && <div style={{ margin: '16px 0 40px 8px' }}>{children}</div>}
    </div>
  );
}
