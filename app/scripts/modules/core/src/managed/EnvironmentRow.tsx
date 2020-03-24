import React, { useState } from 'react';
import classNames from 'classnames';

import './EnvironmentRow.less';

interface IEnvironmentRowProps {
  name: string;
  isProd?: boolean;
  children?: React.ReactNode;
}

export function EnvironmentRow({ name, isProd = false, children }: IEnvironmentRowProps) {
  const [isCollapsed, setIsCollapsed] = useState(false);

  const envRowClasses = classNames({
    srow: true,
    rowProd: isProd,
  });

  const envLabelClasses = classNames({
    envLabel: true,
    prod: isProd,
    nonprod: !isProd,
  });

  return (
    <div className="EnvironmentRow">
      <div className={envRowClasses}>
        <span className="clickableArea">
          <span className={envLabelClasses}>{name}</span>
        </span>
        <div className="expand" onClick={() => setIsCollapsed(!isCollapsed)}>
          {isCollapsed && <i className="ico icon-expand" />}
          {!isCollapsed && <i className="ico icon-collapse" />}
        </div>
        {/* <div className="select">
            <i className={`ico icon-checkbox-unchecked`}/>
          </div> */}
      </div>

      {!isCollapsed && <div style={{ margin: '16px 0 40px 8px' }}>{children}</div>}
    </div>
  );
}
