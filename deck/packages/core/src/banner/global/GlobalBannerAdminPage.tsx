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

import React, { useCallback, useEffect, useState } from 'react';

import { CreateEditBannerModal } from './CreateEditBannerModal';
import { DeleteBannerButton } from './DeleteBannerButton';
import type { IBannerRecord } from './GlobalBannerService';
import { GlobalBannerService } from './GlobalBannerService';

export function GlobalBannerAdminPage() {
  const [banners, setBanners] = useState<IBannerRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [editing, setEditing] = useState<IBannerRecord | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    GlobalBannerService.getAllBanners()
      .then((data) => {
        setBanners(data);
        setLoading(false);
      })
      .catch(() => {
        setError('Failed to load banners');
        setLoading(false);
      });
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const handleSaved = () => {
    setShowCreate(false);
    setEditing(null);
    load();
  };

  const formatTs = (ms?: number) =>
    ms ? new Date(ms).toLocaleString() : <span className="text-muted">—</span>;

  return (
    <div className="container">
      {showCreate && (
        <CreateEditBannerModal onClose={() => setShowCreate(false)} onSaved={handleSaved} />
      )}
      {editing && (
        <CreateEditBannerModal existing={editing} onClose={() => setEditing(null)} onSaved={handleSaved} />
      )}

      <div className="content-section">
        <div className="flex-container-h baseline" style={{ marginBottom: 16 }}>
          <h3 style={{ margin: 0, flex: 1 }}>Global Banners</h3>
          <button className="btn btn-primary btn-sm" onClick={() => setShowCreate(true)}>
            <span className="glyphicon glyphicon-plus" /> New Banner
          </button>
        </div>

        {loading && (
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

        {!loading && !error && banners.length === 0 && (
          <div className="text-center text-muted" style={{ padding: 40 }}>
            No banners configured. Click <strong>New Banner</strong> to create one.
          </div>
        )}

        {!loading && !error && banners.length > 0 && (
          <div className="table-responsive">
            <table className="table table-condensed table-bordered">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Message</th>
                  <th>Enabled</th>
                  <th>Start</th>
                  <th>End</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {banners.map((b) => (
                  <tr key={b.id}>
                    <td>
                      <code>{b.id}</code>
                    </td>
                    <td style={{ maxWidth: 320, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {b.message}
                    </td>
                    <td>
                      {b.enabled ? (
                        <span className="label label-success">enabled</span>
                      ) : (
                        <span className="label label-default">disabled</span>
                      )}
                    </td>
                    <td>{formatTs(b.startTimestamp)}</td>
                    <td>{formatTs(b.endTimestamp)}</td>
                    <td style={{ whiteSpace: 'nowrap' }}>
                      <button
                        className="btn btn-default btn-xs"
                        style={{ marginRight: 4 }}
                        onClick={() => setEditing(b)}
                      >
                        Edit
                      </button>
                      <DeleteBannerButton bannerId={b.id} onDeleted={load} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
