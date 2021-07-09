import React from 'react';
import { mock } from 'angular';
import { mount } from 'enzyme';

import { AuthenticationService } from '../../authentication';
import { REACT_MODULE } from '../../reactShims';
import { IPermissions, IPermissionsConfigurerProps, PermissionsConfigurer } from './PermissionsConfigurer';

describe('PermissionsConfigurer', () => {
  const createComponent = (props: IPermissionsConfigurerProps) => {
    return mount(<PermissionsConfigurer {...props} />).instance() as PermissionsConfigurer;
  };

  beforeEach(mock.module(REACT_MODULE));

  beforeEach(
    mock.inject(() => {
      spyOn(AuthenticationService, 'getAuthenticatedUser').and.callFake(() => {
        return { roles: ['groupA', 'groupB', 'groupC'] } as any;
      });
    }),
  );

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
});
