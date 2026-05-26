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

import React, { useCallback, useEffect, useState } from 'react';
import { Modal } from 'react-bootstrap';

import type { IApiTokenServiceAccount, IApiToken } from './ApiTokenService';
import { ApiTokenService } from './ApiTokenService';
import { CreateApiTokenModal } from './CreateApiTokenModal';
import { RevokeApiTokenButton } from './RevokeApiTokenButton';

import './ApiTokensPage.less';

export interface IApiTokensPageProps {
  isAdmin: boolean;
  /**
   * Whether the user may mint personal tokens (from GET /auth/user → canMintApiTokens).
   * When false, the "New Token" button is hidden; SA-token creation is gated on admin status.
   */
  canMintApiTokens: boolean;
  /** From GET /auth/user. Always pass the server value — never hard-code a fallback. */
  maxUserTokenLifetimeDays: number;
  /** From GET /auth/user. Always pass the server value — never hard-code a fallback. */
  maxServiceAccountTokenLifetimeDays: number;
}

interface INewTokenDisplay {
  token: string;
  name: string;
}

export function ApiTokensPage({
  isAdmin,
  canMintApiTokens,
  maxUserTokenLifetimeDays,
  maxServiceAccountTokenLifetimeDays,
}: IApiTokensPageProps) {
  const [tokens, setTokens] = useState<IApiToken[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState<'user' | 'serviceAccount' | null>(null);
  const [newTokenDisplay, setNewTokenDisplay] = useState<INewTokenDisplay | null>(null);
  const [copied, setCopied] = useState(false);
  const [serviceAccounts, setServiceAccounts] = useState<IApiTokenServiceAccount[]>([]);
  const [allUserTokens, setAllUserTokens] = useState<IApiToken[]>([]);
  const [allUserTokensLoading, setAllUserTokensLoading] = useState(false);

  const loadTokens = useCallback(() => {
    setLoading(true);
    ApiTokenService.listTokens()
      .then((data) => {
        setTokens(data);
        setLoading(false);
      })
      .catch(() => {
        setError('Failed to load tokens');
        setLoading(false);
      });
  }, []);

  const loadAllUserTokens = useCallback(() => {
    if (!isAdmin) return;
    setAllUserTokensLoading(true);
    ApiTokenService.listAllUserTokens()
      .then((data) => {
        setAllUserTokens(data);
        setAllUserTokensLoading(false);
      })
      .catch(() => setAllUserTokensLoading(false));
  }, [isAdmin]);

  useEffect(() => {
    loadTokens();
  }, [loadTokens]);

  useEffect(() => {
    if (isAdmin) {
      ApiTokenService.listApiTokenServiceAccounts()
        .then(setServiceAccounts)
        .catch(() => setServiceAccounts([]));
      loadAllUserTokens();
    }
  }, [isAdmin, loadAllUserTokens]);

  const handleCreated = (token: IApiToken & { token: string }) => {
    setShowCreate(null);
    setNewTokenDisplay({ token: token.token, name: token.name });
    loadTokens();
    if (isAdmin) loadAllUserTokens();
  };

  const handleCopy = () => {
    if (newTokenDisplay) {
      navigator.clipboard.writeText(newTokenDisplay.token).then(() => {
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
      });
    }
  };

  const userTokens = tokens.filter((t) => t.principalType === 'USER');
  const saTokens = tokens.filter((t) => t.principalType === 'SERVICE_ACCOUNT');

  const formatExpiry = (expiresAt: string | null | undefined) => {
    if (!expiresAt) {
      return <span className="text-muted">Never</span>;
    }
    const date = new Date(expiresAt);
    const expired = date < new Date();
    return (
      <span className={expired ? 'text-danger' : undefined}>
        {date.toLocaleDateString()}
        {expired ? ' (expired)' : ''}
      </span>
    );
  };

  const renderEmptyState = (context: 'user' | 'serviceAccount') => {
    const userBlurb = canMintApiTokens
      ? 'Create a personal API token to authenticate programmatic requests as yourself.'
      : 'You do not have permission to create personal API tokens. Contact a Spinnaker administrator if you need access.';
    return (
      <div className="text-center ApiTokensPage-empty">
        <span className="glyphicon glyphicon-lock ApiTokensPage-empty-icon" />
        <p className="ApiTokensPage-empty-title">No tokens yet</p>
        <p className="text-muted ApiTokensPage-empty-body">
          {context === 'user'
            ? userBlurb
            : 'Create a service account token to allow automation to call the Spinnaker API.'}
        </p>
      </div>
    );
  };

  const renderTokenTable = (rows: IApiToken[], context: 'user' | 'serviceAccount', onRevoked?: () => void) => {
    if (rows.length === 0) {
      return renderEmptyState(context);
    }
    const handleRevoked = onRevoked ?? loadTokens;
    return (
      <div className="table-responsive">
        <table className="table table-condensed table-bordered">
          <thead>
            <tr>
              <th>Name</th>
              {context === 'serviceAccount' && <th>Service Account</th>}
              <th>Created By</th>
              <th>Expires</th>
              <th>Last Used</th>
              <th className="ApiTokensPage-actions-column" />
            </tr>
          </thead>
          <tbody>
            {rows.map((t) => (
              <tr key={t.id}>
                <td>{t.name}</td>
                {context === 'serviceAccount' && (
                  <td>
                    <code>{t.principalId}</code>
                  </td>
                )}
                <td>{t.createdByUserId}</td>
                <td>{formatExpiry(t.expiresAt)}</td>
                <td>
                  {t.lastUsedAt ? (
                    new Date(t.lastUsedAt).toLocaleDateString()
                  ) : (
                    <span className="text-muted">never</span>
                  )}
                </td>
                <td className="ApiTokensPage-actions-column">
                  <RevokeApiTokenButton tokenId={t.id} tokenName={t.name} onRevoked={handleRevoked} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  };

  return (
    <div className="container ApiTokensPage">
      {/* One-time token display modal */}
      {newTokenDisplay && (
        <Modal show onHide={() => setNewTokenDisplay(null)}>
          <Modal.Header closeButton>
            <Modal.Title>Token Created: {newTokenDisplay.name}</Modal.Title>
          </Modal.Header>
          <Modal.Body>
            <div className="alert alert-warning">
              <strong>Copy this token now.</strong> It will not be shown again.
            </div>
            <div className="input-group">
              <input type="text" className="form-control" readOnly value={newTokenDisplay.token} />
              <span className="input-group-btn">
                <button className="btn btn-default" onClick={handleCopy}>
                  {copied ? '✓ Copied' : 'Copy'}
                </button>
              </span>
            </div>
          </Modal.Body>
          <Modal.Footer>
            <button className="btn btn-primary" onClick={() => setNewTokenDisplay(null)}>
              Done
            </button>
          </Modal.Footer>
        </Modal>
      )}

      {/* Create modals */}
      {showCreate && (
        <CreateApiTokenModal
          context={showCreate}
          maxLifetimeDays={
            showCreate === 'serviceAccount' ? maxServiceAccountTokenLifetimeDays : maxUserTokenLifetimeDays
          }
          serviceAccounts={showCreate === 'serviceAccount' ? serviceAccounts : undefined}
          existingTokenNames={showCreate === 'user' ? new Set(userTokens.map((t) => t.name)) : undefined}
          onClose={() => setShowCreate(null)}
          onCreated={handleCreated}
        />
      )}

      {/* Global load/error states */}
      {loading && (
        <div className="text-center ApiTokensPage-status">
          <span className="fa fa-spinner fa-spin fa-2x text-muted" />
          <p className="text-muted ApiTokensPage-status-subtitle">Loading tokens…</p>
        </div>
      )}

      {!loading && error && (
        <div className="text-center ApiTokensPage-status">
          <span className="glyphicon glyphicon-exclamation-sign text-danger ApiTokensPage-status-icon" />
          <p className="ApiTokensPage-status-title">Failed to load tokens</p>
          <p className="text-muted">There was a problem communicating with the API. Please try again.</p>
          <button className="btn btn-default" onClick={loadTokens}>
            Retry
          </button>
        </div>
      )}

      {!loading && !error && (
        <>
          <div className="content-section ApiTokensPage-section">
            <div className="flex-container-h baseline ApiTokensPage-section-header">
              <h3>My Tokens</h3>
              {canMintApiTokens && (
                <button className="btn btn-primary btn-sm" onClick={() => setShowCreate('user')}>
                  <span className="glyphicon glyphicon-plus" /> New Token
                </button>
              )}
            </div>
            {renderTokenTable(userTokens, 'user')}
          </div>

          {isAdmin && (
            <>
              <div className="content-section ApiTokensPage-section">
                <div className="flex-container-h baseline ApiTokensPage-section-header">
                  <h3>Service Account Tokens</h3>
                  <span
                    title={
                      serviceAccounts.length === 0
                        ? 'No service accounts are configured. Add entries under service-accounts in front50.yml.'
                        : undefined
                    }
                  >
                    <button
                      className="btn btn-primary btn-sm"
                      onClick={() => setShowCreate('serviceAccount')}
                      disabled={serviceAccounts.length === 0}
                    >
                      <span className="glyphicon glyphicon-plus" /> New SA Token
                    </button>
                  </span>
                </div>
                {serviceAccounts.length === 0 ? (
                  <div className="text-center ApiTokensPage-empty">
                    <span className="glyphicon glyphicon-cog ApiTokensPage-empty-icon" />
                    <p className="ApiTokensPage-empty-title">No service accounts configured</p>
                    <p className="text-muted ApiTokensPage-empty-body">
                      Add entries under <code>service-accounts</code> in <code>front50.yml</code> to enable service
                      account tokens.
                    </p>
                  </div>
                ) : (
                  renderTokenTable(saTokens, 'serviceAccount', () => {
                    loadTokens();
                    loadAllUserTokens();
                  })
                )}
              </div>

              <div className="content-section ApiTokensPage-section">
                <div className="flex-container-h baseline ApiTokensPage-section-header">
                  <h3>All User Tokens</h3>
                  <span className="label label-default ApiTokensPage-admin-badge">Admin view</span>
                </div>
                {allUserTokensLoading ? (
                  <div className="text-center ApiTokensPage-inline-loader">
                    <span className="fa fa-spinner fa-spin text-muted" />
                  </div>
                ) : allUserTokens.length === 0 ? (
                  <div className="text-center ApiTokensPage-empty">
                    <span className="glyphicon glyphicon-user ApiTokensPage-empty-icon" />
                    <p className="ApiTokensPage-empty-title">No user tokens</p>
                    <p className="text-muted ApiTokensPage-empty-body">No users have created API tokens yet.</p>
                  </div>
                ) : (
                  <div className="table-responsive">
                    <table className="table table-condensed table-bordered">
                      <thead>
                        <tr>
                          <th>Name</th>
                          <th>User</th>
                          <th>Expires</th>
                          <th>Last Used</th>
                          <th className="ApiTokensPage-actions-column" />
                        </tr>
                      </thead>
                      <tbody>
                        {allUserTokens.map((t) => (
                          <tr key={t.id}>
                            <td>{t.name}</td>
                            <td>{t.principalId}</td>
                            <td>{formatExpiry(t.expiresAt)}</td>
                            <td>
                              {t.lastUsedAt ? (
                                new Date(t.lastUsedAt).toLocaleDateString()
                              ) : (
                                <span className="text-muted">never</span>
                              )}
                            </td>
                            <td className="ApiTokensPage-actions-column">
                              <RevokeApiTokenButton
                                tokenId={t.id}
                                tokenName={t.name}
                                onRevoked={() => {
                                  loadTokens();
                                  loadAllUserTokens();
                                }}
                              />
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </>
          )}
        </>
      )}
    </div>
  );
}
