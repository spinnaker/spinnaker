import React from 'react';

import './PreDeploymentRow.less';

interface IPreDeploymentRowProps {
  children?: React.ReactNode;
}

export function PreDeploymentRow({ children }: IPreDeploymentRowProps) {
  return (
    <div className="PreDeploymentRow">
      <div className="srow">
        <div className="titleColumn text-bold flex-container-h left middle sp-margin-s-right">PRE-DEPLOYMENT</div>
      </div>

      <div style={{ margin: '16px 0 40px 8px' }}>{children}</div>
    </div>
  );
}
