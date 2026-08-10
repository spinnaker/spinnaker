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

import { shallow } from 'enzyme';
import React from 'react';

import type { IBannerRecord } from './GlobalBannerService';
import { GlobalBannerService } from './GlobalBannerService';
import { CreateEditBannerModal, datetimeLocalToMs, msToDatetimeLocal } from './CreateEditBannerModal';

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

function makeWrapper(props: Partial<React.ComponentProps<typeof CreateEditBannerModal>> = {}) {
  const defaults = {
    onClose: jasmine.createSpy('onClose'),
    onSaved: jasmine.createSpy('onSaved'),
  };
  return shallow(<CreateEditBannerModal {...defaults} {...props} />);
}

// ---------------------------------------------------------------------------
// Create mode
// ---------------------------------------------------------------------------

describe('<CreateEditBannerModal /> — create mode', () => {
  it('renders "Create Banner" title', () => {
    const wrapper = makeWrapper();
    expect(wrapper.find('ModalTitle').prop('children')).toBe('Create Banner');
  });

  it('ID field is enabled', () => {
    const wrapper = makeWrapper();
    expect(wrapper.find('#banner-id').prop('disabled')).toBe(false);
  });

  it('submit button is disabled when ID is empty', () => {
    const wrapper = makeWrapper();
    expect(wrapper.find('button[type="submit"]').prop('disabled')).toBe(true);
  });

  it('submit button is disabled when message is empty', () => {
    const wrapper = makeWrapper();
    wrapper.find('#banner-id').simulate('change', { target: { value: 'my-banner' } });
    expect(wrapper.find('button[type="submit"]').prop('disabled')).toBe(true);
  });

  it('submit button is enabled when ID and message are filled', () => {
    const wrapper = makeWrapper();
    wrapper.find('#banner-id').simulate('change', { target: { value: 'my-banner' } });
    wrapper.find('#banner-message').simulate('change', { target: { value: 'Hello world' } });
    expect(wrapper.find('button[type="submit"]').prop('disabled')).toBe(false);
  });

  it('shows an ID validation error for invalid characters', () => {
    const wrapper = makeWrapper();
    wrapper.find('#banner-id').simulate('change', { target: { value: 'bad id!' } });
    expect(wrapper.find('#banner-id').closest('.form-group').find('.help-block').text()).toContain(
      'letters, numbers, hyphens and underscores',
    );
  });

  it('enabled checkbox defaults to checked', () => {
    const wrapper = makeWrapper();
    expect(wrapper.find('#banner-enabled').prop('checked')).toBe(true);
  });

  it('schedule section is collapsed by default', () => {
    const wrapper = makeWrapper();
    // The inner schedule content is only rendered when open
    expect(wrapper.find('#banner-start').exists()).toBe(false);
  });

  it('clicking the schedule button expands the section', () => {
    const wrapper = makeWrapper();
    wrapper.find('button.btn-link').simulate('click');
    expect(wrapper.find('#banner-start').exists()).toBe(true);
  });

  it('shows end-time validation error when end ≤ start', () => {
    const wrapper = makeWrapper();
    wrapper.find('button.btn-link').simulate('click'); // open schedule

    wrapper.find('#banner-start').simulate('change', { target: { value: '2026-06-01T10:00' } });
    wrapper.find('#banner-end').simulate('change', { target: { value: '2026-06-01T09:00' } });

    expect(wrapper.find('.has-error .help-block').text()).toContain('End time must be after start time');
  });

  it('live preview appears once message is non-empty', () => {
    const wrapper = makeWrapper();
    expect(wrapper.find('.create-edit-banner-modal-preview').exists()).toBe(false);
    wrapper.find('#banner-message').simulate('change', { target: { value: 'Hello' } });
    expect(wrapper.find('.create-edit-banner-modal-preview').exists()).toBe(true);
  });

  it('calls GlobalBannerService.saveBanner and onSaved on successful submit', async () => {
    const saved = { ...BANNER_FIXTURE };
    spyOn(GlobalBannerService, 'saveBanner').and.returnValue(Promise.resolve(saved));
    const onSaved = jasmine.createSpy('onSaved');

    const wrapper = makeWrapper({ onSaved });
    wrapper.find('#banner-id').simulate('change', { target: { value: 'maint-2026' } });
    wrapper.find('#banner-message').simulate('change', { target: { value: 'Maintenance window' } });

    await wrapper.find('form').prop('onSubmit')({ preventDefault: () => {} } as any);

    expect(GlobalBannerService.saveBanner).toHaveBeenCalledWith(
      jasmine.objectContaining({ id: 'maint-2026', message: 'Maintenance window' }),
    );
    expect(onSaved).toHaveBeenCalledWith(saved);
  });

  it('shows error alert when saveBanner rejects', async () => {
    spyOn(GlobalBannerService, 'saveBanner').and.returnValue(Promise.reject({ data: { message: 'Server error' } }));

    const wrapper = makeWrapper();
    wrapper.find('#banner-id').simulate('change', { target: { value: 'x' } });
    wrapper.find('#banner-message').simulate('change', { target: { value: 'msg' } });

    await wrapper.find('form').prop('onSubmit')({ preventDefault: () => {} } as any);
    wrapper.update();

    expect(wrapper.find('.alert-danger').text()).toContain('Server error');
  });

  it('calls onClose when Cancel is clicked', () => {
    const onClose = jasmine.createSpy('onClose');
    const wrapper = makeWrapper({ onClose });
    wrapper
      .find('button[type="button"]')
      .filterWhere((b) => b.text() === 'Cancel')
      .simulate('click');
    expect(onClose).toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------------------
// Edit mode
// ---------------------------------------------------------------------------

describe('<CreateEditBannerModal /> — edit mode', () => {
  it('renders "Edit Banner: {id}" title', () => {
    const wrapper = makeWrapper({ existing: BANNER_FIXTURE });
    expect(wrapper.find('ModalTitle').prop('children')).toBe(`Edit Banner: ${BANNER_FIXTURE.id}`);
  });

  it('ID field is disabled', () => {
    const wrapper = makeWrapper({ existing: BANNER_FIXTURE });
    expect(wrapper.find('#banner-id').prop('disabled')).toBe(true);
  });

  it('pre-populates all text fields from existing banner', () => {
    const wrapper = makeWrapper({ existing: BANNER_FIXTURE });
    expect(wrapper.find('#banner-id').prop('value')).toBe(BANNER_FIXTURE.id);
    expect(wrapper.find('#banner-message').prop('value')).toBe(BANNER_FIXTURE.message);
  });

  it('pre-populates enabled checkbox', () => {
    const wrapper = makeWrapper({ existing: { ...BANNER_FIXTURE, enabled: false } });
    expect(wrapper.find('#banner-enabled').prop('checked')).toBe(false);
  });

  it('schedule section auto-expands when existing banner has timestamps', () => {
    const withSchedule: IBannerRecord = { ...BANNER_FIXTURE, startTimestamp: Date.now() + 60000 };
    const wrapper = makeWrapper({ existing: withSchedule });
    expect(wrapper.find('#banner-start').exists()).toBe(true);
  });

  it('submit button label is "Save" in edit mode', () => {
    const wrapper = makeWrapper({ existing: BANNER_FIXTURE });
    expect(wrapper.find('button[type="submit"]').text()).toBe('Save');
  });

  it('preserves existing createdAt when saving', async () => {
    const saved = { ...BANNER_FIXTURE };
    spyOn(GlobalBannerService, 'saveBanner').and.returnValue(Promise.resolve(saved));

    const wrapper = makeWrapper({ existing: BANNER_FIXTURE });
    await wrapper.find('form').prop('onSubmit')({ preventDefault: () => {} } as any);

    const callArg: IBannerRecord = (GlobalBannerService.saveBanner as jasmine.Spy).calls.mostRecent().args[0];
    expect(callArg.createdAt).toBe(BANNER_FIXTURE.createdAt);
  });
});

// ---------------------------------------------------------------------------
// Colour selects
// ---------------------------------------------------------------------------

describe('<CreateEditBannerModal /> colour controls', () => {
  it('text-colour Select defaults to DEFAULT_COLOR', () => {
    const wrapper = makeWrapper();
    const selects = wrapper.find('Select');
    expect(selects.at(0).prop('value')).toBe('var(--color-text-on-dark)');
  });

  it('background-colour Select defaults to DEFAULT_BG', () => {
    const wrapper = makeWrapper();
    const selects = wrapper.find('Select');
    expect(selects.at(1).prop('value')).toBe('var(--color-alert)');
  });

  it('textarea is styled with the selected colour values', () => {
    const wrapper = makeWrapper({ existing: BANNER_FIXTURE });
    const style = wrapper.find('#banner-message').prop('style') as React.CSSProperties;
    expect(style.color).toBe(BANNER_FIXTURE.color);
    expect(style.backgroundColor).toBe(BANNER_FIXTURE.backgroundColor);
  });
});
