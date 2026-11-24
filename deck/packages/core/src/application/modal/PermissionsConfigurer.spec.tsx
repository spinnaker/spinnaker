import { fireEvent, render, screen, within } from '@testing-library/react';
import React from 'react';

import type { IPermissions } from './PermissionsConfigurer';
import { PermissionsConfigurer } from './PermissionsConfigurer';
import { AuthenticationService } from '../../authentication';

describe('PermissionsConfigurer', () => {
  beforeEach(() => {
    vi.spyOn(AuthenticationService, 'getAuthenticatedUser').mockReturnValue({
      roles: ['groupA', 'groupB', 'groupC'],
    } as any);
  });

  it('converts legacy requiredGroupMembership list to permissions object', () => {
    let permissions: IPermissions;
    render(
      <PermissionsConfigurer
        permissions={null}
        requiredGroupMembership={['groupA', 'groupB']}
        onPermissionsChange={(p: IPermissions) => {
          permissions = p;
        }}
      />,
    );

    expect(permissions).toEqual({
      READ: ['groupA', 'groupB'],
      EXECUTE: ['groupA', 'groupB'],
      WRITE: ['groupA', 'groupB'],
    });
  });

  it(`populates the 'roleOptions' list with a user's roles minus the roles already used in the permissions object`, () => {
    render(
      <PermissionsConfigurer
        permissions={{ READ: ['groupA', 'groupB'], EXECUTE: ['groupB'], WRITE: ['groupB'] }}
        requiredGroupMembership={null}
        onPermissionsChange={() => null}
      />,
    );
    fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Group for permission 1' }));
    const menu = screen.getByRole('listbox');

    expect(within(menu).getByRole('option', { name: 'groupC' })).toBeInTheDocument();
    expect(within(menu).queryByRole('option', { name: 'groupA' })).not.toBeInTheDocument();
    expect(within(menu).queryByRole('option', { name: 'groupB' })).not.toBeInTheDocument();
  });

  it.each([
    {
      name: 'WRITE-only',
      permissions: { READ: [], EXECUTE: [], WRITE: ['groupA'] },
      rawAccess: 'WRITE',
      expected: { READ: [], EXECUTE: [], WRITE: ['groupC'] },
    },
    {
      name: 'EXECUTE-only',
      permissions: { READ: [], EXECUTE: ['groupA'], WRITE: [] },
      rawAccess: 'EXECUTE',
      expected: { READ: [], EXECUTE: ['groupC'], WRITE: [] },
    },
    {
      name: 'EXECUTE and WRITE without READ',
      permissions: { READ: [], EXECUTE: ['groupA'], WRITE: ['groupA'] },
      rawAccess: 'EXECUTE,WRITE',
      expected: { READ: [], EXECUTE: ['groupC'], WRITE: ['groupC'] },
    },
  ])(
    'renders and preserves malformed persisted $name permissions when changing the group',
    ({ permissions, rawAccess, expected }) => {
      const onPermissionsChange = vi.fn();
      render(
        <PermissionsConfigurer
          permissions={permissions}
          requiredGroupMembership={null}
          onPermissionsChange={onPermissionsChange}
        />,
      );

      expect(screen.getByRole('combobox', { name: 'Access for permission 1' })).toBeInTheDocument();
      expect(screen.getByText(rawAccess, { selector: '.Select-value-label' })).toBeInTheDocument();

      fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Group for permission 1' }));
      fireEvent.mouseDown(within(screen.getByRole('listbox')).getByRole('option', { name: 'groupC' }));

      expect(onPermissionsChange).toHaveBeenLastCalledWith(expected);
    },
  );
});
