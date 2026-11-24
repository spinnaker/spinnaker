import React from 'react';
import { Dropdown, Glyphicon, MenuItem } from 'react-bootstrap';

import type { ThemePreference } from '../presentation/theme';
import { useThemePreference } from '../presentation/theme';

import './ThemeMenu.less';

const OPTIONS: Array<{ value: ThemePreference; label: string }> = [
  { value: 'light', label: 'Light' },
  { value: 'dark', label: 'Dark' },
  { value: 'system', label: 'System' },
];

export const ThemeMenu = () => {
  const [preference, setPreference] = useThemePreference();

  return (
    <li className="theme-menu">
      <Dropdown id="theme-menu-dropdown" pullRight={true}>
        <Dropdown.Toggle noCaret={true} title="Theme" aria-label="Theme">
          <Glyphicon glyph="adjust" />
        </Dropdown.Toggle>
        <Dropdown.Menu>
          {OPTIONS.map(({ value, label }) => (
            <MenuItem
              key={value}
              active={preference === value}
              aria-checked={preference === value}
              role="menuitemradio"
              onClick={() => setPreference(value)}
            >
              {label}
            </MenuItem>
          ))}
        </Dropdown.Menu>
      </Dropdown>
    </li>
  );
};
