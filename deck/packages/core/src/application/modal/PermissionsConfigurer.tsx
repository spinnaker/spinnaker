import { cloneDeep, compact, intersection, uniq, without } from 'lodash';
import React from 'react';
import { Button } from 'react-bootstrap';
import type { Option } from 'react-select';
import Select, { Creatable } from 'react-select';

import { AuthenticationService } from '../../authentication';

import './PermissionsConfigurer.less';

export interface IPermissions {
  READ: string[];
  EXECUTE: string[];
  WRITE: string[];
}

export interface IPermissionRow {
  group: string;
  access: string;
}

export interface IPermissionsConfigurerProps {
  permissions: IPermissions;
  /**
   * Grants every application receives from the global default application permissions. They are
   * not part of this application's own ACL, so they are shown for context but cannot be edited or
   * removed here — only changing the server's `authz.application.default-permissions` revokes them.
   */
  defaultPermissions?: IPermissions;
  requiredGroupMembership: string[];
  onPermissionsChange: (permissions: IPermissions) => void;
}

export interface IPermissionsConfigurerState {
  permissionRows: IPermissionRow[];
  defaultPermissionRows: IPermissionRow[];
  roleOptions: Option[];
}

const AUTHORIZATIONS: Array<keyof IPermissions> = ['READ', 'EXECUTE', 'WRITE'];

function mergePermissions(permissions: IPermissions, other: IPermissions): IPermissions {
  if (!permissions || !other) {
    return permissions || other;
  }
  const merged: IPermissions = { READ: [], EXECUTE: [], WRITE: [] };
  AUTHORIZATIONS.forEach((authorization) => {
    merged[authorization] = uniq((permissions[authorization] || []).concat(other[authorization] || []));
  });
  return merged;
}

function subtractPermissions(permissions: IPermissions, toRemove: IPermissions): IPermissions {
  if (!permissions || !toRemove) {
    return permissions;
  }
  const remaining: IPermissions = { READ: [], EXECUTE: [], WRITE: [] };
  AUTHORIZATIONS.forEach((authorization) => {
    remaining[authorization] = without(permissions[authorization] || [], ...(toRemove[authorization] || []));
  });
  return remaining;
}

export class PermissionsConfigurer extends React.Component<IPermissionsConfigurerProps, IPermissionsConfigurerState> {
  private static accessTypes: Option[] = [
    { value: 'READ', label: 'Read only' },
    { value: 'READ,EXECUTE', label: 'Read and execute' },
    { value: 'READ,EXECUTE,WRITE', label: 'Read, execute, write' },
  ];
  private static legacyAccessTypes: Option[] = [{ value: 'READ,WRITE', label: 'Read and write' }];

  constructor(props: IPermissionsConfigurerProps) {
    super(props);
    this.state = this.getState(props);
    if (this.props.requiredGroupMembership && this.props.requiredGroupMembership.length) {
      this.props.onPermissionsChange(this.convertRequiredGroupMembershipToPermissions());
    }
  }

  public componentWillReceiveProps(nextProps: IPermissionsConfigurerProps): void {
    this.setState(this.getState(nextProps));
  }

  private getState(props: IPermissionsConfigurerProps): IPermissionsConfigurerState {
    // `permissions` arrives as the effective ACL, so the defaults have to come back out to leave
    // the grid holding only what this application actually grants and what a save can change.
    return {
      permissionRows: this.getPermissionRows(subtractPermissions(props.permissions, props.defaultPermissions)),
      defaultPermissionRows: this.getPermissionRows(props.defaultPermissions),
      roleOptions: this.getRoleOptions(mergePermissions(props.permissions, props.defaultPermissions)),
    };
  }

  /** Everything the caller is granted, whether by this application's ACL or by the defaults. */
  private effectivePermissions(): IPermissions {
    return mergePermissions(this.props.permissions, this.props.defaultPermissions);
  }

  private getPermissionRows(permissions: IPermissions): IPermissionRow[] {
    const permissionRows: IPermissionRow[] = [];
    if (!permissions) {
      return permissionRows;
    }

    permissions.READ &&
      permissions.READ.forEach((group) => {
        permissionRows.push({ group, access: 'READ' });
      });

    permissions.EXECUTE &&
      permissions.EXECUTE.forEach((group) => {
        const matchingRow = permissionRows.find((row) => row.group === group);
        if (matchingRow) {
          matchingRow.access += ',EXECUTE';
        } else {
          permissionRows.push({ group, access: 'EXECUTE' });
        }
      });

    permissions.WRITE &&
      permissions.WRITE.forEach((group) => {
        const matchingRow = permissionRows.find((row) => row.group === group);
        if (matchingRow) {
          matchingRow.access += ',WRITE';
        } else {
          // WRITE only permissions aren't supported in the UI, but they could be.
          permissionRows.push({ group, access: 'WRITE' });
        }
      });

    return permissionRows;
  }

  private getRoleOptions(permissions: IPermissions): Option[] {
    const availableRoles = AuthenticationService.getAuthenticatedUser().roles;
    return without(
      availableRoles || [],
      ...(permissions
        ? (permissions.READ || []).concat(permissions.WRITE || []).concat(permissions.EXECUTE || [])
        : []),
    ).map((role) => ({ value: role, label: role }));
  }

  private convertRequiredGroupMembershipToPermissions(): IPermissions {
    let READ: string[] = [];
    let WRITE: string[] = [];
    if (this.props.permissions && this.props.permissions.READ) {
      READ = this.props.permissions.READ.slice();
    }
    if (this.props.permissions && this.props.permissions.WRITE) {
      WRITE = this.props.permissions.WRITE.slice();
    }

    this.props.requiredGroupMembership.forEach((group) => {
      READ.push(group);
      WRITE.push(group);
    });

    READ = uniq(READ);
    WRITE = uniq(WRITE);
    return { READ, EXECUTE: WRITE, WRITE };
  }

  private buildPermissions(permissionRows: IPermissionRow[]): IPermissions {
    const permissions: IPermissions = { READ: [], EXECUTE: [], WRITE: [] };
    permissionRows.forEach((row) => {
      const accessTypes = row.access.split(',');
      accessTypes.forEach((type) => {
        if (type === 'READ') {
          permissions.READ.push(row.group);
        } else if (type === 'EXECUTE') {
          permissions.EXECUTE.push(row.group);
        } else if (type === 'WRITE') {
          permissions.WRITE.push(row.group);
        }
      });
    });
    return permissions;
  }

  // Both lockout warnings ask "who will still be able to get in after this save", so they have to
  // consider the defaults too: a role granted globally keeps its access regardless of this grid.
  private willApplicationLockoutForUser(): boolean {
    const effective = this.effectivePermissions();
    const configuredPermissions = effective ? (effective.READ || []).concat(effective.WRITE || []) : [];
    if (compact(configuredPermissions).length) {
      const userRoles = AuthenticationService.getAuthenticatedUser().roles || [];
      return intersection(configuredPermissions, userRoles).length === 0;
    } else {
      return false;
    }
  }

  private willApplicationLockoutAllUsers(): boolean {
    const effective = this.effectivePermissions();
    return !!effective && compact(effective.READ).length > 0 && compact(effective.WRITE).length === 0;
  }

  private handleRoleSelect(rowIndex: number): (option: Option) => void {
    return (option: Option) => {
      const permissionRows = cloneDeep(this.state.permissionRows);
      permissionRows[rowIndex].group = option.value as string;
      this.props.onPermissionsChange(this.buildPermissions(permissionRows));
    };
  }

  private handleAccessTypeSelect(rowIndex: number): (option: Option) => void {
    return (option: Option) => {
      const permissionRows = cloneDeep(this.state.permissionRows);
      permissionRows[rowIndex].access = option.value as string;
      this.props.onPermissionsChange(this.buildPermissions(permissionRows));
    };
  }

  private handleDeletePermission(rowIndex: number): (event: React.MouseEvent<HTMLElement>) => void {
    return () => {
      const permissionRows = cloneDeep(this.state.permissionRows);
      permissionRows.splice(rowIndex, 1);
      this.props.onPermissionsChange(this.buildPermissions(permissionRows));
    };
  }

  private handleAddPermission = (): void => {
    const permissionRows = cloneDeep(this.state.permissionRows);
    permissionRows.push({ group: null, access: 'READ' });
    this.props.onPermissionsChange(this.buildPermissions(permissionRows));
  };

  private static accessLabel(access: string): string {
    const match = [...PermissionsConfigurer.accessTypes, ...PermissionsConfigurer.legacyAccessTypes].find(
      (type) => type.value === access,
    );
    // Defaults are operator-configured and need not spell out one of the combinations the grid
    // offers (a WRITE-only default is legal), so fall back to showing the raw access string.
    return match ? match.label : access;
  }

  public render() {
    return (
      <div className="permissions-configurer">
        {this.state.defaultPermissionRows.map((row) => (
          <div key={`default-${row.group}`} className="permissions-row clearfix">
            <div className="col-md-5 permissions-group">
              <Select value={{ value: row.group, label: row.group }} disabled={true} clearable={false} />
            </div>
            <div className="col-md-6">
              <Select
                value={{ value: row.access, label: PermissionsConfigurer.accessLabel(row.access) }}
                disabled={true}
                clearable={false}
              />
            </div>
            <div className="col-md-1" />
          </div>
        ))}
        {this.state.defaultPermissionRows.length > 0 && (
          <div className="row">
            <div className="col-md-11 default-permissions-note">
              <p className="small">
                The roles above are granted to every application by the Spinnaker configuration, so they cannot be
                changed here.
              </p>
            </div>
          </div>
        )}
        {this.state.permissionRows.map((row, i) => {
          const permissionTypeLabel = PermissionsConfigurer.accessLabel(row.access);
          return (
            <div key={`own-${row.group || i}`} className="permissions-row clearfix">
              <div className="col-md-5 permissions-group">
                <Creatable
                  clearable={false}
                  value={{ value: row.group, label: row.group }}
                  options={this.state.roleOptions}
                  onChange={this.handleRoleSelect(i)}
                />
              </div>
              <div className="col-md-6">
                <Select
                  value={{ value: row.access, label: permissionTypeLabel }}
                  options={PermissionsConfigurer.accessTypes}
                  onChange={this.handleAccessTypeSelect(i)}
                  clearable={false}
                />
              </div>
              <div className="col-md-1 delete-permissions">
                <a onClick={this.handleDeletePermission(i)} className="clickable">
                  <span className="glyphicon glyphicon-trash" />
                </a>
              </div>
            </div>
          );
        })}
        <div className="row">
          <div className="col-md-11">
            <Button className="btn btn-block add-new small" onClick={this.handleAddPermission}>
              <span className="glyphicon glyphicon-plus-sign" /> Add
            </Button>
          </div>
        </div>
        {this.willApplicationLockoutForUser() && (
          <div className="col-md-11">
            <div className="alert alert-warning">
              <p>
                <i className="fa fa-exclamation-triangle" />
                The permissions you have selected will lock you out of this application.
              </p>
            </div>
          </div>
        )}
        {this.willApplicationLockoutAllUsers() && (
          <div className="col-md-11">
            <div className="alert alert-warning">
              <p>
                <i className="fa fa-exclamation-triangle" />
                The permissions you have selected will lock ALL users out of this application.
              </p>
            </div>
          </div>
        )}
      </div>
    );
  }
}
