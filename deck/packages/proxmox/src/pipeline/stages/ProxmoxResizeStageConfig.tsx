import React, { useEffect } from 'react';

import type { IStageConfigProps } from '@spinnaker/core';

import { ProxmoxServerGroupStageConfig } from './ProxmoxServerGroupStageConfig';

/**
 * Resize stage configuration: target selection plus the desired instance count. Scale-up clones
 * new VMs from the template the group was deployed from; scale-down removes the newest first.
 */
export function ProxmoxResizeStageConfig(props: IStageConfigProps) {
  const { stage, updateStage } = props;

  useEffect(() => {
    if (!stage.capacity) {
      stage.capacity = { min: 1, max: 1, desired: 1 };
      updateStage(stage);
    }
  }, []);

  const updateDesired = (desired: number) => {
    stage.capacity = { min: desired, max: desired, desired };
    updateStage(stage);
  };

  return (
    <>
      <ProxmoxServerGroupStageConfig {...props} />
      <div className="form-horizontal">
        <div className="form-group">
          <label className="col-md-3 sm-label-right">Desired Instances</label>
          <div className="col-md-7">
            <input
              type="number"
              className="form-control input-sm"
              min={0}
              value={stage.capacity?.desired ?? 1}
              onChange={(e) => updateDesired(Number(e.target.value))}
            />
          </div>
        </div>
      </div>
    </>
  );
}
