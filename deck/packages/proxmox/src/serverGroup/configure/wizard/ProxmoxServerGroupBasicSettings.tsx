import type { FormikProps } from 'formik';
import React from 'react';

import type { Application, IServerGroup } from '@spinnaker/core';
import {
  AccountSelectInput,
  AccountService,
  HelpField,
  NameUtils,
  ReactInjector,
  ServerGroupNamePreview,
} from '@spinnaker/core';

import type { IProxmoxTemplateImage } from '../../../image/proxmoxImage.reader';
import { listProxmoxTemplates } from '../../../image/proxmoxImage.reader';
import type { IProxmoxServerGroupCommand } from '../proxmoxServerGroupCommandBuilder';

export interface IProxmoxServerGroupBasicSettingsProps {
  formik: FormikProps<IProxmoxServerGroupCommand>;
  app: Application;
}

export interface IProxmoxServerGroupBasicSettingsState {
  nodes: string[];
  storagePools: string[];
  templates: IProxmoxTemplateImage[];
}

export class ProxmoxServerGroupBasicSettings extends React.Component<
  IProxmoxServerGroupBasicSettingsProps,
  IProxmoxServerGroupBasicSettingsState
> {
  public state: IProxmoxServerGroupBasicSettingsState = { nodes: [], storagePools: [], templates: [] };

  public componentDidMount(): void {
    this.loadAccountData(this.props.formik.values.credentials);
  }

  public componentDidUpdate(prevProps: IProxmoxServerGroupBasicSettingsProps): void {
    if (prevProps.formik.values.credentials !== this.props.formik.values.credentials) {
      this.loadAccountData(this.props.formik.values.credentials);
    }
  }

  private loadAccountData(credentials: string): void {
    if (!credentials) {
      return;
    }
    AccountService.getAccountDetails(credentials).then((details: any) => {
      this.setState({
        nodes: (details?.regions ?? []).map((r: any) => r.name ?? r),
        storagePools: details?.storagePools ?? [],
      });
    });
    listProxmoxTemplates(credentials).then((templates) => this.setState({ templates }));
  }

  private setValue = (field: keyof IProxmoxServerGroupCommand, value: any): void => {
    this.props.formik.setFieldValue(field, value);
  };

  private templateChanged = (value: string): void => {
    const { setFieldValue } = this.props.formik;
    if (!value) {
      setFieldValue('templateVmid', undefined);
      setFieldValue('templateNode', '');
      return;
    }
    const [vmid, node] = value.split('|');
    setFieldValue('templateVmid', Number(vmid));
    setFieldValue('templateNode', node);
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

  private selectOrText(
    label: React.ReactNode,
    options: Array<{ label: string; value: string }>,
    value: string,
    onChange: (value: string) => void,
    emptyOptionLabel?: string,
  ): JSX.Element {
    return (
      <div className="form-group">
        <div className="col-md-3 sm-label-right">{label}</div>
        <div className="col-md-7">
          {options.length > 0 ? (
            <select className="form-control input-sm" value={value ?? ''} onChange={(e) => onChange(e.target.value)}>
              {emptyOptionLabel != null && <option value="">{emptyOptionLabel}</option>}
              {options.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          ) : (
            <input
              type="text"
              className="form-control input-sm no-spel"
              value={value ?? ''}
              onChange={(e) => onChange(e.target.value)}
            />
          )}
        </div>
      </div>
    );
  }

  public render() {
    const { formik, app } = this.props;
    const { values } = formik;
    const { nodes, storagePools, templates } = this.state;
    const forPipeline = values.viewState.mode === 'editPipeline' || values.viewState.mode === 'createPipeline';

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

    const typedTemplates = templates.filter((t) => (values.vmType === 'lxc' ? t.vmType === 'lxc' : t.vmType !== 'lxc'));
    const selectedTemplate = values.templateVmid != null ? `${values.templateVmid}|${values.templateNode ?? ''}` : '';
    const templateOptions = typedTemplates.map((t) => ({
      label: `${t.imageName} (vmid ${t.vmid} on ${t.region})`,
      value: `${t.vmid}|${t.region}`,
    }));
    // Keep a stale selection visible even if the template no longer exists in the cache.
    if (selectedTemplate && !templateOptions.some((o) => o.value === selectedTemplate)) {
      templateOptions.unshift({
        label: `vmid ${values.templateVmid} (${values.templateNode || 'unknown node'})`,
        value: selectedTemplate,
      });
    }

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

        {this.selectOrText(
          <>
            Node <HelpField content="Proxmox node the new server group will be created on." />
          </>,
          nodes.map((node) => ({ label: node, value: node })),
          values.region,
          (value) => this.setValue('region', value),
          '(select a node)',
        )}

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
            Template{' '}
            <HelpField
              content={
                forPipeline
                  ? 'Template to clone. Leave unset to use the template produced by a preceding Bake stage.'
                  : 'Template to clone the new server group from.'
              }
            />
          </div>
          <div className="col-md-7">
            <select
              className="form-control input-sm"
              value={selectedTemplate}
              onChange={(e) => this.templateChanged(e.target.value)}
            >
              <option value="">{forPipeline ? '(use template from Bake stage)' : '(select a template)'}</option>
              {templateOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>
        </div>

        {this.selectOrText(
          <>
            Storage <HelpField content="Storage pool for the cloned disk. Required for full clones." />
          </>,
          storagePools.map((pool) => ({ label: pool, value: pool })),
          values.storage,
          (value) => this.setValue('storage', value),
        )}

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
