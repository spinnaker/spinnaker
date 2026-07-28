import { mount } from 'enzyme';
import React from 'react';

import type { IPermissions, IPermissionsConfigurerProps } from './PermissionsConfigurer';
import { PermissionsConfigurer } from './PermissionsConfigurer';
import { AuthenticationService } from '../../authentication';

describe('PermissionsConfigurer', () => {
  const createComponent = (props: IPermissionsConfigurerProps) => {
    return mount(<PermissionsConfigurer {...props} />).instance() as PermissionsConfigurer;
  };

  beforeEach(() => {
    spyOn(AuthenticationService, 'getAuthenticatedUser').and.returnValue({
      roles: ['groupA', 'groupB', 'groupC'],
    } as any);
  });

  it('converts legacy requiredGroupMembership list to permissions object', () => {
    let permissions: IPermissions;
    createComponent({
      permissions: null,
      requiredGroupMembership: ['groupA', 'groupB'],
      onPermissionsChange: (p: IPermissions) => {
        permissions = p;
      },
    });

    expect(permissions).toEqual({
      READ: ['groupA', 'groupB'],
      EXECUTE: ['groupA', 'groupB'],
      WRITE: ['groupA', 'groupB'],
    });
  });

  it(`populates the 'roleOptions' list with a user's roles minus the roles already used in the permissions object`, () => {
    const component = createComponent({
      permissions: { READ: ['groupA', 'groupB'], EXECUTE: ['groupB'], WRITE: ['groupB'] },
      requiredGroupMembership: null,
      onPermissionsChange: () => null,
    });

    expect(component.state.roleOptions.map((option) => option.value)).toEqual(['groupC']);
  });

  it('separates globally granted roles from the rows the application actually owns', () => {
    const component = createComponent({
      // What the server returns is the effective ACL: the app's own grants plus the defaults.
      permissions: { READ: ['groupA', 'everyone'], EXECUTE: ['groupA'], WRITE: ['groupA'] },
      defaultPermissions: { READ: ['everyone'], EXECUTE: [], WRITE: [] },
      requiredGroupMembership: null,
      onPermissionsChange: () => null,
    });

    expect(component.state.permissionRows).toEqual([{ group: 'groupA', access: 'READ,EXECUTE,WRITE' }]);
    expect(component.state.defaultPermissionRows).toEqual([{ group: 'everyone', access: 'READ' }]);
  });

  it("submits only the application's own grants, so a default is never stored as an explicit grant", () => {
    let submitted: IPermissions;
    const component = createComponent({
      permissions: { READ: ['groupA', 'everyone'], EXECUTE: [], WRITE: ['groupA'] },
      defaultPermissions: { READ: ['everyone'], EXECUTE: [], WRITE: [] },
      requiredGroupMembership: null,
      onPermissionsChange: (p: IPermissions) => {
        submitted = p;
      },
    });

    // Editing the one row the application owns fires a change carrying everything the form saves.
    (component as any).handleAccessTypeSelect(0)({ value: 'READ,EXECUTE,WRITE' });

    expect(submitted).toEqual({ READ: ['groupA'], EXECUTE: ['groupA'], WRITE: ['groupA'] });
  });

  it('does not warn about lockout when the user only has access through the defaults', () => {
    const component = createComponent({
      permissions: { READ: ['someoneElse', 'groupA'], EXECUTE: [], WRITE: ['someoneElse'] },
      defaultPermissions: { READ: ['groupA'], EXECUTE: [], WRITE: ['groupA'] },
      requiredGroupMembership: null,
      onPermissionsChange: () => null,
    });

    expect((component as any).willApplicationLockoutForUser()).toBe(false);
  });

  it('still warns about lockout when neither the ACL nor the defaults cover the user', () => {
    const component = createComponent({
      permissions: { READ: ['someoneElse'], EXECUTE: [], WRITE: ['someoneElse'] },
      defaultPermissions: { READ: ['anotherTeam'], EXECUTE: [], WRITE: ['anotherTeam'] },
      requiredGroupMembership: null,
      onPermissionsChange: () => null,
    });

    expect((component as any).willApplicationLockoutForUser()).toBe(true);
  });

  it('behaves as before when no defaults are configured', () => {
    const component = createComponent({
      permissions: { READ: ['groupA'], EXECUTE: [], WRITE: ['groupA'] },
      requiredGroupMembership: null,
      onPermissionsChange: () => null,
    });

    expect(component.state.permissionRows).toEqual([{ group: 'groupA', access: 'READ,WRITE' }]);
    expect(component.state.defaultPermissionRows).toEqual([]);
  });
});
