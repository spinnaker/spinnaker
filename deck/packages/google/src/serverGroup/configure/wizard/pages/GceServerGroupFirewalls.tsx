import React from 'react';

import type { IGceServerGroupCommand } from '../GceServerGroupWizard.types';
import { GceServerGroupWizardPage } from '../GceServerGroupWizardPage';

interface IFirewall {
  id: string;
  name: string;
  network?: string;
  targetTags: string[];
}

interface IFirewallOption {
  firewall: IFirewall;
  unavailable: boolean;
}

interface ITag {
  value: string;
  [key: string]: unknown;
}

export class GceServerGroupFirewalls extends GceServerGroupWizardPage {
  private firewallsChanged = (event: React.ChangeEvent<HTMLSelectElement>): void => {
    const values = this.props.formik.values;
    const securityGroups = unique(Array.from(event.target.selectedOptions).map((option) => option.value));
    const available = scopedFirewalls(values);
    const removedTags = available
      .filter((firewall) => selectedFirewallIds(values).includes(firewall.id) && !securityGroups.includes(firewall.id))
      .flatMap((firewall) => firewall.targetTags);
    const tags = selectedTags(values).filter((tag) => !removedTags.includes(tag.value));
    this.reconcile({ ...values, securityGroups, tags }, securityGroups, tags);
  };

  private targetTagChanged = (tagName: string, selected: boolean): void => {
    const values = this.props.formik.values;
    const available = explicitFirewalls(values);
    const tags = selected
      ? uniqueTags([...selectedTags(values), { value: tagName }])
      : selectedTags(values).filter((tag) => tag.value !== tagName);
    const selectedTagNames = tags.map((tag) => tag.value);
    const currentlySelected = selectedFirewallIds(values);
    const unavailable = currentlySelected.filter((id) => !available.some((firewall) => firewall.id === id));
    const securityGroups = selected
      ? unique([
          ...currentlySelected,
          ...available.filter((firewall) => firewall.targetTags.includes(tagName)).map((firewall) => firewall.id),
        ])
      : unique([
          ...unavailable,
          ...available
            .filter(
              (firewall) =>
                currentlySelected.includes(firewall.id) &&
                firewall.targetTags.some((targetTag) => selectedTagNames.includes(targetTag)),
            )
            .map((firewall) => firewall.id),
        ]);
    this.reconcile({ ...values, securityGroups, tags }, securityGroups, tags);
  };

  private reconcile = (command: IGceServerGroupCommand, securityGroups: string[], tags: ITag[]): void => {
    void this.runLatestCommandRequest(command, async (latestCommand) => {
      const update = await this.adapter.applyCommandHandler(latestCommand, 'networkChanged');
      return preserveFirewallSelection(update.command, securityGroups, tags);
    });
  };

  private showImplicitChanged = (showImplicit: boolean): void => {
    const values = this.props.formik.values;
    this.props.formik.setFieldValue('viewState', {
      ...values.viewState,
      listImplicitSecurityGroups: showImplicit,
    });
  };

  private refreshFirewalls = (): void => {
    const command = this.props.formik.values;
    const securityGroups = selectedFirewallIds(command);
    const tags = selectedTags(command);
    void this.runLatestCommandRequest(command, async (latestCommand) => {
      const update = await this.adapter.applyConfigurationRefresh(latestCommand, 'refreshSecurityGroups');
      return preserveFirewallSelection(update.command, securityGroups, tags);
    });
  };

  public render(): JSX.Element {
    const values = this.props.formik.values;
    const selectedIds = selectedFirewallIds(values);
    const tags = selectedTags(values).map((tag) => tag.value);
    const explicit = explicitFirewalls(values);
    const implicit = implicitFirewalls(values);
    const options = firewallOptions(explicit, selectedIds);
    const selectedKnownFirewalls = explicit.filter((firewall) => selectedIds.includes(firewall.id));

    return (
      <div className="container-fluid form-horizontal">
        <div className="form-group">
          <label className="col-md-3 sm-label-right" htmlFor="gce-server-group-firewalls">
            Firewalls
          </label>
          <div className="col-md-9">
            <select
              aria-label="Firewalls"
              className="form-control input-sm"
              id="gce-server-group-firewalls"
              multiple={true}
              onChange={this.firewallsChanged}
              value={selectedIds}
            >
              {options.map(({ firewall, unavailable }) => (
                <option key={firewall.id} value={firewall.id}>
                  {unavailable ? `${firewall.id} (unavailable)` : `${firewall.name} (${firewall.id})`}
                </option>
              ))}
            </select>
            {!options.length && <p className="form-control-static">No explicit firewalls found.</p>}
          </div>
        </div>

        {selectedKnownFirewalls.map((firewall) => (
          <fieldset className="form-group" key={firewall.id}>
            <legend className="col-md-3 sm-label-right">Target Tags for {firewall.name}</legend>
            <div className="col-md-9">
              {firewall.targetTags.map((targetTag) => {
                const id = controlId('gce-firewall-target-tag', firewall.id, targetTag);
                return (
                  <div className="checkbox" key={targetTag}>
                    <label htmlFor={id}>
                      <input
                        aria-label={`Target tag ${targetTag} for firewall ${firewall.id}`}
                        checked={tags.includes(targetTag)}
                        id={id}
                        onChange={(event) => this.targetTagChanged(targetTag, event.target.checked)}
                        type="checkbox"
                      />
                      {targetTag}
                    </label>
                  </div>
                );
              })}
            </div>
          </fieldset>
        ))}

        <div className="form-group small">
          <div className="col-md-9 col-md-offset-3 checkbox">
            <label htmlFor="gce-show-implicit-firewalls">
              <input
                aria-label="Show implicit firewalls"
                checked={Boolean(values.viewState.listImplicitSecurityGroups)}
                id="gce-show-implicit-firewalls"
                onChange={(event) => this.showImplicitChanged(event.target.checked)}
                type="checkbox"
              />{' '}
              Show implicit firewalls
            </label>
          </div>
        </div>

        {values.viewState.listImplicitSecurityGroups && (
          <div className="form-group">
            <div className="col-md-3 sm-label-right">
              <b>Implicit Firewalls</b>
            </div>
            <div className="col-md-9">
              <ul aria-label="Implicit firewalls">
                {implicit.length ? (
                  implicit.map((firewall) => <li key={firewall.id}>{firewall.name}</li>)
                ) : (
                  <li>None</li>
                )}
              </ul>
            </div>
          </div>
        )}

        <div className="form-group small">
          <div className="col-md-9 col-md-offset-3">
            <button
              aria-label="Refresh firewalls"
              className="btn btn-link"
              onClick={this.refreshFirewalls}
              type="button"
            >
              Refresh firewalls
            </button>
          </div>
        </div>
      </div>
    );
  }
}

function firewallOptions(available: IFirewall[], selectedIds: string[]): IFirewallOption[] {
  const availableIds = available.map((firewall) => firewall.id);
  return [
    ...available.map((firewall) => ({ firewall, unavailable: false })),
    ...selectedIds
      .filter((id) => !availableIds.includes(id))
      .map((id) => ({ firewall: { id, name: id, targetTags: [] }, unavailable: true })),
  ];
}

function explicitFirewalls(command: IGceServerGroupCommand): IFirewall[] {
  return scopedFirewalls(command).filter((firewall) => firewall.targetTags.length > 0);
}

function implicitFirewalls(command: IGceServerGroupCommand): IFirewall[] {
  return scopedFirewalls(command).filter((firewall) => firewall.targetTags.length === 0);
}

function scopedFirewalls(command: IGceServerGroupCommand): IFirewall[] {
  const raw = command.backingData?.securityGroups?.[command.credentials || '']?.gce?.global || [];
  const byId = new Map<string, IFirewall>();
  raw
    .filter((firewall: any) => firewall.network === command.network)
    .forEach((firewall: any) => {
      const existing = byId.get(firewall.id);
      const targetTags = unique([...(existing?.targetTags || []), ...parseTargetTags(firewall.targetTags)]);
      byId.set(firewall.id, {
        id: firewall.id,
        name: firewall.name || existing?.name || firewall.id,
        network: firewall.network,
        targetTags,
      });
    });
  return Array.from(byId.values()).sort((left, right) => left.name.localeCompare(right.name));
}

function parseTargetTags(targetTags: unknown): string[] {
  if (Array.isArray(targetTags)) {
    return unique(targetTags.map(String));
  }
  if (typeof targetTags !== 'string') {
    return [];
  }
  const content = targetTags.trim().replace(/^\[/, '').replace(/\]$/, '');
  return unique(content.split(',').map((tag) => tag.trim()));
}

function selectedFirewallIds(command: IGceServerGroupCommand): string[] {
  return unique(command.securityGroups || []);
}

function selectedTags(command: IGceServerGroupCommand): ITag[] {
  return uniqueTags((command.tags || []).map((tag: string | ITag) => (typeof tag === 'string' ? { value: tag } : tag)));
}

function uniqueTags(tags: ITag[]): ITag[] {
  const byValue = new Map<string, ITag>();
  tags.forEach((tag) => tag.value && !byValue.has(tag.value) && byValue.set(tag.value, tag));
  return Array.from(byValue.values());
}

function preserveFirewallSelection(
  command: IGceServerGroupCommand,
  securityGroups: string[],
  tags: ITag[],
): IGceServerGroupCommand {
  return {
    ...command,
    securityGroups,
    tags,
    viewState: {
      ...command.viewState,
      dirty: { ...command.viewState.dirty, securityGroups: null },
    },
  };
}

function controlId(...parts: string[]): string {
  return parts.join('-').replace(/[^a-zA-Z0-9_-]/g, '-');
}

function unique(values: string[]): string[] {
  return Array.from(new Set(values.filter(Boolean)));
}
