import type { FormikProps } from 'formik';
import React from 'react';

import type { Application, IServerGroup } from '@spinnaker/core';
import { AccountSelectInput, HelpField, NameUtils, ReactInjector, ServerGroupNamePreview } from '@spinnaker/core';

import type { IProxmoxServerGroupCommand } from '../proxmoxServerGroupCommandBuilder';

export interface IProxmoxServerGroupBasicSettingsProps {
  formik: FormikProps<IProxmoxServerGroupCommand>;
  app: Application;
}

export class ProxmoxServerGroupBasicSettings extends React.Component<IProxmoxServerGroupBasicSettingsProps> {
  private setValue = (field: keyof IProxmoxServerGroupCommand, value: any): void => {
    this.props.formik.setFieldValue(field, value);
  };

  private navigateToLatestServerGroup = (latestServerGroup: IServerGroup): void => {
    const params = {
      provider: 'proxmox',
      accountId: latestServerGroup.account,
      region: latestServerGroup.region,
      serverGroup: latestServerGroup.name,
    };
    const { $state } = ReactInjector;
    if ($state.is('home.applications.application.insight.clusters')) {
      $state.go('.serverGroup', params);
    } else {
      $state.go('^.serverGroup', params);
    }
  };

  public render() {
    const { formik, app } = this.props;
    const { values } = formik;

    const namePreview = NameUtils.getClusterName(app.name, values.stack, values.freeFormDetails);
    const createsNewCluster = !app.clusters.find((c) => c.name === namePreview);
    const inCluster = (app.serverGroups.data as IServerGroup[])
      .filter(
        (serverGroup) =>
          serverGroup.cluster === namePreview &&
          serverGroup.account === values.credentials &&
          serverGroup.region === values.region,
      )
      .sort((a, b) => a.createdTime - b.createdTime);
    const latestServerGroup = inCluster.length ? inCluster[inCluster.length - 1] : null;

    return (
      <div className="form-horizontal">
        <div className="form-group">
          <div className="col-md-3 sm-label-right">Account</div>
          <div className="col-md-7">
            <AccountSelectInput
              value={values.credentials}
              onChange={(evt: any) => this.setValue('credentials', evt.target.value)}
              readOnly={false}
              accounts={values.backingData?.accounts ?? []}
              provider="proxmox"
            />
          </div>
        </div>

        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            Node <HelpField content="Proxmox node the new server group will be created on (e.g. pve01)." />
          </div>
          <div className="col-md-7">
            <input
              type="text"
              className="form-control input-sm no-spel"
              value={values.region ?? ''}
              onChange={(e) => this.setValue('region', e.target.value)}
            />
          </div>
        </div>

        <div className="form-group">
          <div className="col-md-3 sm-label-right">Stack</div>
          <div className="col-md-7">
            <input
              type="text"
              className="form-control input-sm no-spel"
              value={values.stack ?? ''}
              onChange={(e) => this.setValue('stack', e.target.value)}
            />
          </div>
        </div>

        <div className="form-group">
          <div className="col-md-3 sm-label-right">Detail</div>
          <div className="col-md-7">
            <input
              type="text"
              className="form-control input-sm no-spel"
              value={values.freeFormDetails ?? ''}
              onChange={(e) => this.setValue('freeFormDetails', e.target.value)}
            />
          </div>
        </div>

        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            Template VMID <HelpField content="VMID of the source template to clone. Required." />
          </div>
          <div className="col-md-7">
            <input
              type="number"
              className="form-control input-sm no-spel"
              value={values.templateVmid ?? ''}
              onChange={(e) => this.setValue('templateVmid', e.target.value ? Number(e.target.value) : undefined)}
            />
          </div>
        </div>

        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            Template Node <HelpField content="Node the template resides on. Defaults to the target node." />
          </div>
          <div className="col-md-7">
            <input
              type="text"
              className="form-control input-sm no-spel"
              value={values.templateNode ?? ''}
              onChange={(e) => this.setValue('templateNode', e.target.value)}
            />
          </div>
        </div>

        <div className="form-group">
          <div className="col-md-3 sm-label-right">Type</div>
          <div className="col-md-7">
            <select
              className="form-control input-sm"
              value={values.vmType}
              onChange={(e) => this.setValue('vmType', e.target.value)}
            >
              <option value="qemu">QEMU VM</option>
              <option value="lxc">LXC Container</option>
            </select>
          </div>
        </div>

        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            Storage <HelpField content="Storage pool for the cloned disk (e.g. local-lvm). Required for full clones." />
          </div>
          <div className="col-md-7">
            <input
              type="text"
              className="form-control input-sm no-spel"
              value={values.storage ?? ''}
              onChange={(e) => this.setValue('storage', e.target.value)}
            />
          </div>
        </div>

        <div className="form-group">
          <div className="col-md-3 sm-label-right">Full Clone</div>
          <div className="col-md-7 checkbox">
            <label>
              <input
                type="checkbox"
                checked={values.fullClone}
                onChange={(e) => this.setValue('fullClone', e.target.checked)}
              />{' '}
              Perform a full independent clone (uncheck for a linked clone)
            </label>
          </div>
        </div>

        {!values.viewState.hideClusterNamePreview && (
          <ServerGroupNamePreview
            createsNewCluster={createsNewCluster}
            latestServerGroupName={latestServerGroup?.name}
            mode={values.viewState.mode}
            namePreview={namePreview}
            navigateToLatestServerGroup={() => this.navigateToLatestServerGroup(latestServerGroup)}
          />
        )}
      </div>
    );
  }
}
