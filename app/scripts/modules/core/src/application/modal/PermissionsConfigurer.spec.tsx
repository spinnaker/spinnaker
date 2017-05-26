import * as React from 'react';
import { mock } from 'angular';
import { mount } from 'enzyme';

import { ReactInjector, REACT_MODULE } from 'core/reactShims';
import {
  IPermissions, IPermissionsConfigurerProps,
  PermissionsConfigurer
} from 'core/application/modal/PermissionsConfigurer';
import { AUTHENTICATION_SERVICE } from 'core/authentication';

describe('PermissionsConfigurer', () => {
  const createComponent = (props: IPermissionsConfigurerProps) => {
    return mount(<PermissionsConfigurer {...props}/>).instance() as PermissionsConfigurer;
  };

  beforeEach(
    mock.module(
      AUTHENTICATION_SERVICE,
      REACT_MODULE
    )
  );

  beforeEach(mock.inject(() => {
    const authenticationService = ReactInjector.authenticationService;
    spyOn(authenticationService, 'getAuthenticatedUser').and.callFake(() => {
      return {roles: ['groupA', 'groupB', 'groupC']};
    });
  }));

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
      WRITE: ['groupA', 'groupB'],
    });
  });

  it(`populates the 'roleOptions' list with a user's roles minus the roles already used in the permissions object`, () => {
    const component = createComponent({
      permissions: {READ: ['groupA', 'groupB'], WRITE: ['groupB']},
      requiredGroupMembership: null,
      onPermissionsChange: () => null,
    });

    expect(component.state.roleOptions.map(option => option.value)).toEqual(['groupC']);
  });
});
