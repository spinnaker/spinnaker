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

import React, { useState } from 'react';
import { Modal } from 'react-bootstrap';
import type { Option } from 'react-select';
import Select from 'react-select';

import type { IBannerRecord } from './GlobalBannerService';
import { GlobalBannerService } from './GlobalBannerService';
import { bannerBackgroundColorOptions, bannerTextColorOptions } from '../../application/config/customBanner/customBannerColors';
import { Markdown } from '../../presentation/Markdown';

const MAX_ID_LENGTH = 64;
const MAX_MESSAGE_LENGTH = 2000;
const VALID_ID_RE = /^[A-Za-z0-9_-]+$/;

// ---------------------------------------------------------------------------
// Helpers: convert between Unix epoch ms and <input type="datetime-local">
// ---------------------------------------------------------------------------

/** Converts a Unix epoch ms value to a "YYYY-MM-DDTHH:mm" local string, or '' if falsy. */
export function msToDatetimeLocal(ms?: number): string {
  if (!ms) return '';
  const d = new Date(ms);
  // toISOString gives UTC; we want local wall-clock time for the input
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** Converts a "YYYY-MM-DDTHH:mm" local string to Unix epoch ms, or undefined if empty. */
export function datetimeLocalToMs(s: string): number | undefined {
  if (!s) return undefined;
  const ms = new Date(s).getTime();
  return isNaN(ms) ? undefined : ms;
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export interface ICreateEditBannerModalProps {
  /** When provided the modal is in edit mode and fields are pre-populated. */
  existing?: IBannerRecord;
  onClose: () => void;
  onSaved: (banner: IBannerRecord) => void;
}

const DEFAULT_COLOR = 'var(--color-text-on-dark)';
const DEFAULT_BG = 'var(--color-alert)';
const DEFAULT_FONT_SIZE = '14px';

const fontSizeOptions: Array<Option<string>> = [
  { label: '12px', value: '12px' },
  { label: '14px', value: '14px' },
  { label: '16px', value: '16px' },
  { label: '18px', value: '18px' },
  { label: '20px', value: '20px' },
  { label: '24px', value: '24px' },
];

export function CreateEditBannerModal({ existing, onClose, onSaved }: ICreateEditBannerModalProps) {
  const isEdit = !!existing;

  const [id, setId] = useState(existing?.id ?? '');
  const [message, setMessage] = useState(existing?.message ?? '');
  const [color, setColor] = useState(existing?.color ?? DEFAULT_COLOR);
  const [backgroundColor, setBackgroundColor] = useState(existing?.backgroundColor ?? DEFAULT_BG);
  const [fontSize, setFontSize] = useState(existing?.fontSize ?? DEFAULT_FONT_SIZE);
  const [enabled, setEnabled] = useState(existing?.enabled ?? true);
  const [scheduleOpen, setScheduleOpen] = useState(!!(existing?.startTimestamp || existing?.endTimestamp));
  const [startDatetime, setStartDatetime] = useState(msToDatetimeLocal(existing?.startTimestamp));
  const [endDatetime, setEndDatetime] = useState(msToDatetimeLocal(existing?.endTimestamp));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // ---------------------------------------------------------------------------
  // Validation
  // ---------------------------------------------------------------------------

  const idTrimmed = id.trim();
  const idInvalidChars = idTrimmed.length > 0 && !VALID_ID_RE.test(idTrimmed);
  const idTooLong = idTrimmed.length > MAX_ID_LENGTH;
  const messageTrimmed = message.trim();
  const messageTooLong = messageTrimmed.length > MAX_MESSAGE_LENGTH;

  const startMs = datetimeLocalToMs(startDatetime);
  const endMs = datetimeLocalToMs(endDatetime);
  const scheduleError = startMs && endMs && endMs <= startMs ? 'End time must be after start time' : null;

  const isSubmittable =
    idTrimmed && !idInvalidChars && !idTooLong && messageTrimmed && !messageTooLong && !scheduleError && !saving;

  // ---------------------------------------------------------------------------
  // Submit
  // ---------------------------------------------------------------------------

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!isSubmittable) return;
    setError(null);
    setSaving(true);

    const record: IBannerRecord = {
      ...(existing ?? {}),
      id: idTrimmed,
      message: messageTrimmed,
      color,
      backgroundColor,
      fontSize,
      enabled,
      startTimestamp: startMs,
      endTimestamp: endMs,
    };

    try {
      const saved = await GlobalBannerService.saveBanner(record);
      onSaved(saved);
    } catch (e: any) {
      setError(e?.data?.message ?? 'Failed to save banner');
      setSaving(false);
    }
  };

  // ---------------------------------------------------------------------------
  // Colour swatch renderer (matches CustomBannerConfig pattern)
  // ---------------------------------------------------------------------------

  const colorOptionRenderer = (option: Option<string>) => (
    <div className="custom-banner-config-color-option" style={{ backgroundColor: option.value }} />
  );

  // ---------------------------------------------------------------------------
  // Render
  // ---------------------------------------------------------------------------

  return (
    <Modal show onHide={onClose}>
      <Modal.Header closeButton>
        <Modal.Title>{isEdit ? `Edit Banner: ${existing.id}` : 'Create Banner'}</Modal.Title>
      </Modal.Header>
      <form onSubmit={handleSubmit}>
        <Modal.Body>
          {error && (
            <div className="alert alert-danger" role="alert">
              {error}
            </div>
          )}

          {/* ID */}
          <div className={`form-group${idInvalidChars || idTooLong ? ' has-error' : ''}`}>
            <label htmlFor="banner-id">ID *</label>
            <input
              id="banner-id"
              className="form-control"
              type="text"
              value={id}
              onChange={(e) => setId(e.target.value)}
              maxLength={MAX_ID_LENGTH}
              required
              autoFocus={!isEdit}
              disabled={isEdit}
              placeholder="e.g. maintenance-2026-06"
            />
            {idInvalidChars && (
              <span className="help-block">ID may only contain letters, numbers, hyphens and underscores</span>
            )}
            {!idInvalidChars && (
              <span className="help-block text-muted">
                {isEdit ? 'ID cannot be changed after creation' : `${idTrimmed.length}/${MAX_ID_LENGTH} characters`}
              </span>
            )}
          </div>

          {/* Message */}
          <div className={`form-group${messageTooLong ? ' has-error' : ''}`}>
            <label htmlFor="banner-message">
              Message *{' '}
              <span className={`pull-right small${messageTooLong ? ' text-danger' : ' text-muted'}`}>
                {messageTrimmed.length}/{MAX_MESSAGE_LENGTH}
              </span>
            </label>
            <textarea
              id="banner-message"
              className="form-control"
              rows={4}
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              style={{ backgroundColor, color, fontSize }}
              required
            />
            <span className="help-block text-muted" style={{ marginTop: 2 }}>
              Markdown is supported
            </span>
            {messageTrimmed && (
              <div>
                <strong>Preview</strong>
                <div
                  className="create-edit-banner-modal-preview"
                  style={{ backgroundColor, color, fontSize, padding: '6px 10px', borderRadius: 3, marginTop: 4 }}
                >
                  <Markdown message={messageTrimmed} />
                </div>
              </div>
            )}
          </div>

          {/* Colours + font size */}
          <div className="row">
            <div className="col-xs-4 form-group">
              <label>Text colour</label>
              <Select
                clearable={false}
                options={bannerTextColorOptions}
                value={color}
                onChange={(option: Option<string>) => setColor(option.value)}
                optionRenderer={colorOptionRenderer}
                valueRenderer={colorOptionRenderer}
              />
            </div>
            <div className="col-xs-4 form-group">
              <label>Background colour</label>
              <Select
                clearable={false}
                options={bannerBackgroundColorOptions}
                value={backgroundColor}
                onChange={(option: Option<string>) => setBackgroundColor(option.value)}
                optionRenderer={colorOptionRenderer}
                valueRenderer={colorOptionRenderer}
              />
            </div>
            <div className="col-xs-4 form-group">
              <label>Font size</label>
              <Select
                clearable={false}
                options={fontSizeOptions}
                value={fontSize}
                onChange={(option: Option<string>) => setFontSize(option.value)}
              />
            </div>
          </div>

          {/* Enabled */}
          <div className="form-group">
            <div className="checkbox">
              <label>
                <input
                  id="banner-enabled"
                  type="checkbox"
                  checked={enabled}
                  onChange={(e) => setEnabled(e.target.checked)}
                />
                {' Enabled'}
              </label>
            </div>
            {startMs && !enabled && (
              <span className="help-block text-warning" style={{ marginTop: 2 }}>
                Banner is disabled. It will not activate at the scheduled start time unless you also check Enabled.
              </span>
            )}
          </div>

          {/* Schedule section */}
          <div className="form-group">
            <button
              type="button"
              className="btn btn-link"
              style={{ paddingLeft: 0 }}
              onClick={() => setScheduleOpen(!scheduleOpen)}
            >
              <span className={`glyphicon glyphicon-chevron-${scheduleOpen ? 'down' : 'right'}`} />
              {' Schedule activation window (optional)'}
            </button>

            {scheduleOpen && (
              <div style={{ paddingLeft: 12, borderLeft: '3px solid #eee', marginTop: 8 }}>
                <div className="form-group" style={{ marginBottom: 8 }}>
                  <label htmlFor="banner-start">Activate at</label>
                  <input
                    id="banner-start"
                    className="form-control"
                    type="datetime-local"
                    value={startDatetime}
                    onChange={(e) => setStartDatetime(e.target.value)}
                  />
                </div>
                <div className={`form-group${scheduleError ? ' has-error' : ''}`} style={{ marginBottom: 4 }}>
                  <label htmlFor="banner-end">Deactivate at</label>
                  <input
                    id="banner-end"
                    className="form-control"
                    type="datetime-local"
                    value={endDatetime}
                    onChange={(e) => setEndDatetime(e.target.value)}
                  />
                  {scheduleError && <span className="help-block">{scheduleError}</span>}
                </div>
                <p className="text-muted" style={{ fontSize: 12, marginBottom: 0 }}>
                  Timestamps are stored as UTC milliseconds. The backend re-evaluates activation every 60 s.
                </p>
              </div>
            )}
          </div>
        </Modal.Body>

        <Modal.Footer>
          <button type="button" className="btn btn-default" onClick={onClose} disabled={saving}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={!isSubmittable}>
            {saving ? 'Saving…' : isEdit ? 'Save' : 'Create Banner'}
          </button>
        </Modal.Footer>
      </form>
    </Modal>
  );
}
