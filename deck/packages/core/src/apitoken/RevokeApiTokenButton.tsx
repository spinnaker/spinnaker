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

import React, { useState } from 'react';
import { Modal } from 'react-bootstrap';

import { ApiTokenService } from './ApiTokenService';

export interface IRevokeApiTokenButtonProps {
  tokenId: string;
  tokenName: string;
  onRevoked: () => void;
}

export function RevokeApiTokenButton({ tokenId, tokenName, onRevoked }: IRevokeApiTokenButtonProps) {
  const [showModal, setShowModal] = useState(false);
  const [revoking, setRevoking] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleRevoke = () => {
    setRevoking(true);
    setError(null);
    ApiTokenService.revokeToken(tokenId)
      .then(() => {
        setShowModal(false);
        onRevoked();
      })
      .catch(() => {
        setRevoking(false);
        setError('Failed to revoke token. Please try again.');
      });
  };

  return (
    <>
      <button className="btn btn-default btn-xs" onClick={() => setShowModal(true)}>
        Revoke
      </button>

      <Modal show={showModal} onHide={() => !revoking && setShowModal(false)} bsSize="small">
        <Modal.Header closeButton={!revoking}>
          <Modal.Title>Revoke token</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {error && (
            <div className="alert alert-danger" role="alert" style={{ marginBottom: 12 }}>
              {error}
            </div>
          )}
          <p>
            Are you sure you want to revoke <strong>{tokenName}</strong>?
          </p>
          <p className="text-muted" style={{ fontSize: 13, marginBottom: 0 }}>
            This cannot be undone. Any automation using this token will stop working immediately.
          </p>
        </Modal.Body>
        <Modal.Footer>
          <button className="btn btn-default" onClick={() => setShowModal(false)} disabled={revoking}>
            Cancel
          </button>
          <button className="btn btn-danger" onClick={handleRevoke} disabled={revoking}>
            {revoking ? 'Revoking…' : 'Revoke token'}
          </button>
        </Modal.Footer>
      </Modal>
    </>
  );
}
