import React, { useEffect, useState } from 'react';

import type { IAccount, IStageConfigProps } from '@spinnaker/core';
import { AccountService, StageConstants } from '@spinnaker/core';

/**
 * Shared configuration form for proxmox server-group pipeline stages (destroy, resize, stop,
 * start): account, node, cluster, and target server group selection.
 */
export function ProxmoxServerGroupStageConfig({ application, stage, updateStage }: IStageConfigProps) {
  const [accounts, setAccounts] = useState<IAccount[]>([]);

  useEffect(() => {
    stage.cloudProvider = 'proxmox';
    if (!stage.target) {
      stage.target = StageConstants.TARGET_LIST[0].val;
    }
    AccountService.listAccounts('proxmox').then((proxmoxAccounts) => {
      setAccounts(proxmoxAccounts);
      if (!stage.credentials && application?.defaultCredentials?.proxmox) {
        stage.credentials = application.defaultCredentials.proxmox;
      }
      updateStage(stage);
    });
  }, []);

  const update = (field: string, value: any) => {
    stage[field] = value;
    updateStage(stage);
  };

  return (
    <div className="form-horizontal">
      <div className="form-group">
        <label className="col-md-3 sm-label-right">Account</label>
        <div className="col-md-7">
          <select
            className="form-control input-sm"
            value={stage.credentials ?? ''}
            onChange={(e) => update('credentials', e.target.value)}
          >
            <option value="" disabled={true}>
              Select an account
            </option>
            {accounts.map((account) => (
              <option key={account.name} value={account.name}>
                {account.name}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="form-group">
        <label className="col-md-3 sm-label-right">Node</label>
        <div className="col-md-7">
          <input
            type="text"
            className="form-control input-sm"
            placeholder="Proxmox node (region), e.g. pve01"
            value={stage.region ?? ''}
            onChange={(e) => update('region', e.target.value)}
          />
        </div>
      </div>

      <div className="form-group">
        <label className="col-md-3 sm-label-right">Cluster</label>
        <div className="col-md-7">
          <input
            type="text"
            className="form-control input-sm"
            placeholder="Cluster name, e.g. myapp-prod"
            value={stage.cluster ?? ''}
            onChange={(e) => update('cluster', e.target.value)}
          />
        </div>
      </div>

      <div className="form-group">
        <label className="col-md-3 sm-label-right">Target</label>
        <div className="col-md-7">
          <select
            className="form-control input-sm"
            value={stage.target ?? ''}
            onChange={(e) => update('target', e.target.value)}
          >
            {StageConstants.TARGET_LIST.map((target) => (
              <option key={target.val} value={target.val}>
                {target.label}
              </option>
            ))}
          </select>
        </div>
      </div>
    </div>
  );
}
