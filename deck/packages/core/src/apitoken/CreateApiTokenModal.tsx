// Copyright 2026 DoorDash, Inc.
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

import React, { useEffect, useState } from 'react';
import { Modal } from 'react-bootstrap';

import type { IApiTokenServiceAccount, IApiToken, ICreateApiTokenRequest } from './ApiTokenService';
import { ApiTokenService } from './ApiTokenService';

export type TokenContext = 'user' | 'serviceAccount';

const MAX_TOKEN_NAME_LENGTH = 64;

export interface ICreateApiTokenModalProps {
  context: TokenContext;
  /**
   * Max lifetime in days for this token type (from GET /auth/user). Caps the date picker.
   * Always pass the server value — never hard-code a fallback.
   */
  maxLifetimeDays: number;
  /** Pre-loaded service accounts; avoids a redundant fetch from the modal. */
  serviceAccounts?: IApiTokenServiceAccount[];
  /** Names already owned by the current principal — for client-side duplicate detection. */
  existingTokenNames?: Set<string>;
  onClose: () => void;
  onCreated: (token: IApiToken & { token: string }) => void;
}

export function CreateApiTokenModal({
  context,
  maxLifetimeDays,
  serviceAccounts: serviceAccountsProp,
  existingTokenNames,
  onClose,
  onCreated,
}: ICreateApiTokenModalProps) {
  const [name, setName] = useState('');
  const [useForever, setUseForever] = useState(true);
  const [customExpiry, setCustomExpiry] = useState('');
  const [selectedSA, setSelectedSA] = useState('');
  const [serviceAccounts, setServiceAccounts] = useState<IApiTokenServiceAccount[]>(serviceAccountsProp ?? []);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const todayStr = new Date().toISOString().slice(0, 10);
  const maxDate = new Date();
  maxDate.setDate(maxDate.getDate() + maxLifetimeDays);
  const maxDateStr = maxDate.toISOString().slice(0, 10);

  useEffect(() => {
    if (context === 'serviceAccount' && !serviceAccountsProp) {
      ApiTokenService.listApiTokenServiceAccounts()
        .then(setServiceAccounts)
        .catch(() => setError('Failed to load service accounts'));
    }
  }, [context, serviceAccountsProp]);

  const handleToggleForever = (forever: boolean) => {
    setUseForever(forever);
    if (forever) {
      setCustomExpiry('');
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setCreating(true);

    try {
      const request: ICreateApiTokenRequest = {
        name,
        principalType: context === 'serviceAccount' ? 'SERVICE_ACCOUNT' : 'USER',
      };

      if (context === 'serviceAccount') {
        if (!selectedSA) {
          setError('Please select a service account');
          setCreating(false);
          return;
        }
        request.principalId = selectedSA;
      }

      // Omitting expiresAt: Gate applies the configured max lifetime.
      if (!useForever && customExpiry) {
        request.expiresAt = new Date(customExpiry).toISOString();
      }

      const created = await ApiTokenService.createToken(request);
      onCreated(created);
    } catch (e: any) {
      setError(e?.data?.message ?? 'Failed to create token');
      setCreating(false);
    }
  };

  const nameTrimmed = name.trim();
  const nameAlreadyTaken = !!existingTokenNames && nameTrimmed.length > 0 && existingTokenNames.has(nameTrimmed);
  const nameTooLong = nameTrimmed.length > MAX_TOKEN_NAME_LENGTH;
  const isSubmittable = nameTrimmed && !nameAlreadyTaken && !nameTooLong && (useForever || !!customExpiry);

  return (
    <Modal show onHide={onClose}>
      <Modal.Header closeButton>
        <Modal.Title>{context === 'serviceAccount' ? 'Create Service Account Token' : 'Create API Token'}</Modal.Title>
      </Modal.Header>
      <form onSubmit={handleSubmit}>
        <Modal.Body>
          {error && (
            <div className="alert alert-danger" role="alert">
              {error}
            </div>
          )}

          <div className={`form-group${nameAlreadyTaken || nameTooLong ? ' has-error' : ''}`}>
            <label htmlFor="token-name">Name</label>
            <input
              id="token-name"
              className="form-control"
              type="text"
              placeholder="e.g. my-ci-pipeline"
              value={name}
              onChange={(e) => setName(e.target.value.slice(0, MAX_TOKEN_NAME_LENGTH))}
              maxLength={MAX_TOKEN_NAME_LENGTH}
              required
              autoFocus
            />
            {nameAlreadyTaken && (
              <span className="help-block">
                You already have a token named &ldquo;{nameTrimmed}&rdquo;. Choose a different name.
              </span>
            )}
            {!nameAlreadyTaken && (
              <span className="help-block text-muted">
                {nameTrimmed.length}/{MAX_TOKEN_NAME_LENGTH} characters
              </span>
            )}
          </div>

          {context === 'serviceAccount' && (
            <div className="form-group">
              <label htmlFor="token-sa">Service Account</label>
              <select
                id="token-sa"
                className="form-control"
                value={selectedSA}
                onChange={(e) => setSelectedSA(e.target.value)}
                required
              >
                <option value="">— select a service account —</option>
                {serviceAccounts.map((sa) => (
                  <option key={sa.name} value={sa.name}>
                    {sa.name}
                  </option>
                ))}
              </select>
            </div>
          )}

          <div className="form-group">
            <label>Expiry</label>
            <div>
              <label style={{ fontWeight: 'normal', marginRight: 16 }}>
                <input
                  type="radio"
                  name="expiry-mode"
                  style={{ marginRight: 6 }}
                  checked={useForever}
                  onChange={() => handleToggleForever(true)}
                />
                {context === 'serviceAccount' ? (
                  <>Never expires</>
                ) : (
                  <>
                    Maximum <small className="text-muted">({maxLifetimeDays} days, set by server policy)</small>
                  </>
                )}
              </label>
              <label style={{ fontWeight: 'normal' }}>
                <input
                  type="radio"
                  name="expiry-mode"
                  style={{ marginRight: 6 }}
                  checked={!useForever}
                  onChange={() => handleToggleForever(false)}
                />
                Custom date <small className="text-muted">(up to {maxLifetimeDays} days)</small>
              </label>
            </div>
            {!useForever && (
              <input
                id="token-expiry"
                className="form-control"
                style={{ marginTop: 8 }}
                type="date"
                min={todayStr}
                max={maxDateStr}
                value={customExpiry}
                onChange={(e) => setCustomExpiry(e.target.value)}
                required
              />
            )}
          </div>
        </Modal.Body>
        <Modal.Footer>
          <button type="button" className="btn btn-default" onClick={onClose} disabled={creating}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={creating || !isSubmittable}>
            {creating ? 'Creating…' : 'Create Token'}
          </button>
        </Modal.Footer>
      </form>
    </Modal>
  );
}
