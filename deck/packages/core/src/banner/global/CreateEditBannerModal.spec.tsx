// Copyright 2026 Harness, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import React from 'react';
import type { Mock } from 'vitest';

import { CreateEditBannerModal, datetimeLocalToMs, msToDatetimeLocal } from './CreateEditBannerModal';
import type { IBannerRecord } from './GlobalBannerService';
import { GlobalBannerService } from './GlobalBannerService';
import { setupUser } from '../../utils/testUtils';

// ---------------------------------------------------------------------------
// Pure helper tests — no DOM required
// ---------------------------------------------------------------------------

describe('msToDatetimeLocal / datetimeLocalToMs', () => {
  it('msToDatetimeLocal returns empty string for falsy input', () => {
    expect(msToDatetimeLocal(0)).toBe('');
    expect(msToDatetimeLocal(undefined)).toBe('');
  });

  it('round-trips a timestamp through both helpers', () => {
    // Use a fixed local time that survives DST safely (noon)
    const original = new Date(2026, 5, 1, 12, 30).getTime(); // Jun 1 2026 12:30 local
    const localStr = msToDatetimeLocal(original);
    expect(localStr).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/);
    expect(datetimeLocalToMs(localStr)).toBe(original);
  });

  it('datetimeLocalToMs returns undefined for empty string', () => {
    expect(datetimeLocalToMs('')).toBeUndefined();
  });
});

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const BANNER_FIXTURE: IBannerRecord = {
  id: 'maint-2026',
  message: '**Maintenance** in 30 min',
  color: 'var(--color-text-on-dark)',
  backgroundColor: 'var(--color-alert)',
  enabled: true,
  createdAt: 1000,
  updatedAt: 2000,
};

function renderModal(props: Partial<React.ComponentProps<typeof CreateEditBannerModal>> = {}) {
  const defaults = {
    onClose: vi.fn(),
    onSaved: vi.fn(),
  };
  return render(<CreateEditBannerModal {...defaults} {...props} />);
}

const idInput = () => screen.getByLabelText('ID *') as HTMLInputElement;
const messageInput = () => screen.getByLabelText(/^Message \*/) as HTMLTextAreaElement;
const enabledCheckbox = () => screen.getByRole('checkbox', { name: 'Enabled' }) as HTMLInputElement;
const scheduleToggle = () => screen.getByRole('button', { name: /Schedule activation window/ });
const submitButton = (name: string | RegExp = /^(Create Banner|Save)$/) =>
  screen.getByRole('button', { name }) as HTMLButtonElement;
const colorSelectValue = (label: string) => {
  const group = screen.getByText(label, { selector: 'label' }).closest('.form-group') as HTMLElement;
  return group.querySelector('.Select-value .custom-banner-config-color-option') as HTMLElement;
};

afterEach(() => {
  vi.restoreAllMocks();
});

// ---------------------------------------------------------------------------
// Create mode
// ---------------------------------------------------------------------------

describe('<CreateEditBannerModal /> — create mode', () => {
  it('renders "Create Banner" title', () => {
    renderModal();
    expect(document.querySelector('.modal-title')).toHaveTextContent(/^Create Banner$/);
  });

  it('ID field is enabled', () => {
    renderModal();
    expect(idInput()).toBeEnabled();
  });

  it('submit button is disabled when ID is empty', () => {
    renderModal();
    expect(submitButton('Create Banner')).toBeDisabled();
  });

  it('submit button is disabled when message is empty', async () => {
    const user = setupUser();
    renderModal();
    await user.type(idInput(), 'my-banner');
    expect(submitButton('Create Banner')).toBeDisabled();
  });

  it('submit button is enabled when ID and message are filled', async () => {
    const user = setupUser();
    renderModal();
    await user.type(idInput(), 'my-banner');
    await user.type(messageInput(), 'Hello world');
    expect(submitButton('Create Banner')).toBeEnabled();
  });

  it('shows an ID validation error for invalid characters', async () => {
    const user = setupUser();
    renderModal();
    await user.type(idInput(), 'bad id!');
    const group = idInput().closest('.form-group') as HTMLElement;
    expect(within(group).getByText(/letters, numbers, hyphens and underscores/)).toHaveClass('help-block');
  });

  it('enabled checkbox defaults to checked', () => {
    renderModal();
    expect(enabledCheckbox()).toBeChecked();
  });

  it('schedule section is collapsed by default', () => {
    renderModal();
    expect(screen.queryByLabelText('Activate at')).not.toBeInTheDocument();
  });

  it('clicking the schedule button expands the section', async () => {
    const user = setupUser();
    renderModal();
    await user.click(scheduleToggle());
    expect(screen.getByLabelText('Activate at')).toBeInTheDocument();
  });

  it('shows end-time validation error when end ≤ start', async () => {
    const user = setupUser();
    renderModal();
    await user.click(scheduleToggle());

    fireEvent.change(screen.getByLabelText('Activate at'), { target: { value: '2026-06-01T10:00' } });
    fireEvent.change(screen.getByLabelText('Deactivate at'), { target: { value: '2026-06-01T09:00' } });

    const group = screen.getByLabelText('Deactivate at').closest('.form-group') as HTMLElement;
    expect(group).toHaveClass('has-error');
    expect(within(group).getByText('End time must be after start time')).toHaveClass('help-block');
  });

  it('live preview appears once message is non-empty', async () => {
    const user = setupUser();
    renderModal();
    expect(screen.queryByText('Preview')).not.toBeInTheDocument();
    await user.type(messageInput(), 'Hello');
    expect(screen.getByText('Preview')).toBeInTheDocument();
    expect(document.querySelector('.create-edit-banner-modal-preview')).toHaveTextContent('Hello');
  });

  it('calls GlobalBannerService.saveBanner and onSaved on successful submit', async () => {
    const user = setupUser();
    const saved = { ...BANNER_FIXTURE };
    vi.spyOn(GlobalBannerService, 'saveBanner').mockReturnValue(Promise.resolve(saved));
    const onSaved = vi.fn();

    renderModal({ onSaved });
    await user.type(idInput(), 'maint-2026');
    await user.type(messageInput(), 'Maintenance window');
    await user.click(submitButton('Create Banner'));

    await waitFor(() => expect(onSaved).toHaveBeenCalledWith(saved));
    expect(GlobalBannerService.saveBanner).toHaveBeenCalledWith(
      expect.objectContaining({ id: 'maint-2026', message: 'Maintenance window' }),
    );
  });

  it('shows error alert when saveBanner rejects', async () => {
    const user = setupUser();
    vi.spyOn(GlobalBannerService, 'saveBanner').mockReturnValue(Promise.reject({ data: { message: 'Server error' } }));

    renderModal();
    await user.type(idInput(), 'x');
    await user.type(messageInput(), 'msg');
    await user.click(submitButton('Create Banner'));

    expect(await screen.findByRole('alert')).toHaveTextContent('Server error');
  });

  it('calls onClose when Cancel is clicked', async () => {
    const user = setupUser();
    const onClose = vi.fn();
    renderModal({ onClose });
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(onClose).toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------------------
// Edit mode
// ---------------------------------------------------------------------------

describe('<CreateEditBannerModal /> — edit mode', () => {
  it('renders "Edit Banner: {id}" title', () => {
    renderModal({ existing: BANNER_FIXTURE });
    expect(document.querySelector('.modal-title')).toHaveTextContent(`Edit Banner: ${BANNER_FIXTURE.id}`);
  });

  it('ID field is disabled', () => {
    renderModal({ existing: BANNER_FIXTURE });
    expect(idInput()).toBeDisabled();
  });

  it('pre-populates all text fields from existing banner', () => {
    renderModal({ existing: BANNER_FIXTURE });
    expect(idInput()).toHaveValue(BANNER_FIXTURE.id);
    expect(messageInput()).toHaveValue(BANNER_FIXTURE.message);
  });

  it('pre-populates enabled checkbox', () => {
    renderModal({ existing: { ...BANNER_FIXTURE, enabled: false } });
    expect(enabledCheckbox()).not.toBeChecked();
  });

  it('schedule section auto-expands when existing banner has timestamps', () => {
    const withSchedule: IBannerRecord = { ...BANNER_FIXTURE, startTimestamp: Date.now() + 60000 };
    renderModal({ existing: withSchedule });
    expect(screen.getByLabelText('Activate at')).toBeInTheDocument();
  });

  it('submit button label is "Save" in edit mode', () => {
    renderModal({ existing: BANNER_FIXTURE });
    expect(submitButton()).toHaveTextContent(/^Save$/);
    expect(submitButton()).toHaveAttribute('type', 'submit');
  });

  it('preserves existing createdAt when saving', async () => {
    const user = setupUser();
    const saved = { ...BANNER_FIXTURE };
    vi.spyOn(GlobalBannerService, 'saveBanner').mockReturnValue(Promise.resolve(saved));
    const onSaved = vi.fn();

    renderModal({ existing: BANNER_FIXTURE, onSaved });
    await user.click(submitButton('Save'));

    await waitFor(() => expect(onSaved).toHaveBeenCalled());
    const callArg: IBannerRecord = (GlobalBannerService.saveBanner as Mock).mock.lastCall[0];
    expect(callArg.createdAt).toBe(BANNER_FIXTURE.createdAt);
  });
});

// ---------------------------------------------------------------------------
// Colour selects
// ---------------------------------------------------------------------------

describe('<CreateEditBannerModal /> colour controls', () => {
  it('text-colour Select defaults to DEFAULT_COLOR', () => {
    renderModal();
    expect(colorSelectValue('Text colour').style.backgroundColor).toBe('var(--color-text-on-dark)');
  });

  it('background-colour Select defaults to DEFAULT_BG', () => {
    renderModal();
    expect(colorSelectValue('Background colour').style.backgroundColor).toBe('var(--color-alert)');
  });

  it('textarea is styled with the selected colour values', () => {
    renderModal({ existing: BANNER_FIXTURE });
    const style = messageInput().style;
    expect(style.color).toBe(BANNER_FIXTURE.color);
    expect(style.backgroundColor).toBe(BANNER_FIXTURE.backgroundColor);
  });
});
