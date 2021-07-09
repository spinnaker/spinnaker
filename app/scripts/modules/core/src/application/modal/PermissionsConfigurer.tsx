import { cloneDeep, compact, intersection, uniq, without } from 'lodash';
import React from 'react';
import { Button } from 'react-bootstrap';
import Select, { Creatable, Option } from 'react-select';

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
  requiredGroupMembership: string[];
  onPermissionsChange: (permissions: IPermissions) => void;
}

export interface IPermissionsConfigurerState {
  permissionRows: IPermissionRow[];
  roleOptions: Option[];
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
    return {
      permissionRows: this.getPermissionRows(props.permissions),
      roleOptions: this.getRoleOptions(props.permissions),
    };
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

  private willApplicationLockoutForUser(): boolean {
    const configuredPermissions = this.props.permissions
      ? (this.props.permissions.READ || []).concat(this.props.permissions.WRITE || [])
      : [];
    if (compact(configuredPermissions).length) {
      const userRoles = AuthenticationService.getAuthenticatedUser().roles || [];
      return intersection(configuredPermissions, userRoles).length === 0;
    } else {
      return false;
    }
  }

  private willApplicationLockoutAllUsers(): boolean {
    return (
      !!this.props.permissions &&
      compact(this.props.permissions.READ).length > 0 &&
      compact(this.props.permissions.WRITE).length === 0
    );
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

  public render() {
    return (
      <div className="permissions-configurer">
        {this.state.permissionRows.map((row, i) => {
          const permissionTypeLabel = [
            ...PermissionsConfigurer.accessTypes,
            ...PermissionsConfigurer.legacyAccessTypes,
          ].find((type) => type.value === row.access).label;
          return (
            <div key={row.group || i} className="permissions-row clearfix">
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
