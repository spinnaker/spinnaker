import React, { useCallback, useEffect, useState } from 'react';

import type { IAccountDefinition } from './AccountManagementService';
import { AccountManagementService } from './AccountManagementService';
import { CreateEditAccountModal } from './CreateEditAccountModal';
import { DeleteAccountButton } from './DeleteAccountButton';
import { ACCOUNT_SAMPLES } from './accountSamples';
import { CloudProviderRegistry } from '../../cloudProvider/CloudProviderRegistry';

const PAGE_SIZE = 100;

export function AccountManagementPage() {
  const [accountType, setAccountType] = useState('kubernetes');
  const [accounts, setAccounts] = useState<IAccountDefinition[]>([]);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [editing, setEditing] = useState<IAccountDefinition | null>(null);

  const providerSuggestions = Array.from(
    new Set([...Object.keys(ACCOUNT_SAMPLES), ...CloudProviderRegistry.listRegisteredProviders()]),
  ).sort();

  const load = useCallback(() => {
    const type = accountType.trim();
    if (!type) {
      return;
    }
    setLoading(true);
    setError(null);
    AccountManagementService.getAccountsByType(type, PAGE_SIZE)
      .then((data) => {
        setAccounts(data);
        setHasMore(data.length === PAGE_SIZE);
        setLoading(false);
      })
      .catch((e: any) => {
        setError(e?.data?.message ?? `Failed to load ${type} accounts`);
        setLoading(false);
      });
  }, [accountType]);

  useEffect(() => {
    load();
  }, [load]);

  const loadMore = () => {
    const type = accountType.trim();
    const last = accounts[accounts.length - 1];
    if (!type || !last) {
      return;
    }
    setLoading(true);
    setError(null);
    // startingAccountName is inclusive, so the first result duplicates the last loaded row
    AccountManagementService.getAccountsByType(type, PAGE_SIZE, last.name)
      .then((data) => {
        setAccounts((existing) => {
          const known = new Set(existing.map((a) => a.name));
          return existing.concat(data.filter((a) => !known.has(a.name)));
        });
        setHasMore(data.length === PAGE_SIZE);
        setLoading(false);
      })
      .catch((e: any) => {
        setError(e?.data?.message ?? `Failed to load ${type} accounts`);
        setLoading(false);
      });
  };

  const handleSaved = () => {
    setShowCreate(false);
    setEditing(null);
    load();
  };

  return (
    <div className="container">
      {showCreate && (
        <CreateEditAccountModal
          defaultType={accountType.trim()}
          onClose={() => setShowCreate(false)}
          onSaved={handleSaved}
        />
      )}
      {editing && <CreateEditAccountModal existing={editing} onClose={() => setEditing(null)} onSaved={handleSaved} />}

      <div className="content-section">
        <div className="flex-container-h baseline" style={{ marginBottom: 16 }}>
          <h3 style={{ margin: 0, flex: 1 }}>Account Management</h3>
          <button className="btn btn-primary btn-sm" onClick={() => setShowCreate(true)}>
            <span className="glyphicon glyphicon-plus" /> New Account
          </button>
        </div>

        <div className="form-group" style={{ maxWidth: 320 }}>
          <label htmlFor="account-type">Account Type</label>
          <input
            id="account-type"
            className="form-control"
            type="text"
            list="account-type-suggestions"
            value={accountType}
            onChange={(e) => setAccountType(e.target.value)}
            placeholder="e.g. kubernetes"
          />
          <datalist id="account-type-suggestions">
            {providerSuggestions.map((provider) => (
              <option key={provider} value={provider} />
            ))}
          </datalist>
          <span className="help-block text-muted">
            Only accounts stored via the account management APIs are listed; accounts from static configuration do not
            appear here.
          </span>
        </div>

        {loading && accounts.length === 0 && (
          <div className="text-center" style={{ padding: 40 }}>
            <span className="glyphicon glyphicon-asterisk glyphicon-spinning" /> Loading…
          </div>
        )}

        {!loading && error && (
          <div className="alert alert-danger">
            {error}{' '}
            <button className="btn btn-default btn-xs" onClick={load}>
              Retry
            </button>
          </div>
        )}

        {!loading && !error && accounts.length === 0 && (
          <div className="text-center text-muted" style={{ padding: 40 }}>
            No <strong>{accountType.trim() || '…'}</strong> accounts found. Click <strong>New Account</strong> to create
            one.
          </div>
        )}

        {accounts.length > 0 && (
          <div className="table-responsive">
            <table className="table table-condensed table-bordered">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Type</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {accounts.map((account) => (
                  <tr key={account.name}>
                    <td>
                      <code>{account.name}</code>
                    </td>
                    <td>{account.type}</td>
                    <td style={{ whiteSpace: 'nowrap', width: 1 }}>
                      <button
                        className="btn btn-default btn-xs"
                        style={{ marginRight: 4 }}
                        onClick={() => setEditing(account)}
                      >
                        Edit
                      </button>
                      <DeleteAccountButton accountName={account.name} onDeleted={load} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {hasMore && (
              <div className="text-center" style={{ marginBottom: 16 }}>
                <button className="btn btn-default btn-sm" onClick={loadMore} disabled={loading}>
                  {loading ? 'Loading…' : 'Load More'}
                </button>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
