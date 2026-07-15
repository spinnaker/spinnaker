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

import { GlobalBannerService } from './GlobalBannerService';

export interface IDeleteBannerButtonProps {
  bannerId: string;
  onDeleted: () => void;
}

export function DeleteBannerButton({ bannerId, onDeleted }: IDeleteBannerButtonProps) {
  const [showModal, setShowModal] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleDelete = () => {
    setDeleting(true);
    setError(null);
    GlobalBannerService.deleteBanner(bannerId)
      .then(() => {
        setShowModal(false);
        onDeleted();
      })
      .catch(() => {
        setDeleting(false);
        setError('Failed to delete banner. Please try again.');
      });
  };

  return (
    <>
      <button className="btn btn-default btn-xs" onClick={() => setShowModal(true)}>
        Delete
      </button>

      <Modal show={showModal} onHide={() => !deleting && setShowModal(false)} bsSize="small">
        <Modal.Header closeButton={!deleting}>
          <Modal.Title>Delete banner</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {error && (
            <div className="alert alert-danger" role="alert" style={{ marginBottom: 12 }}>
              {error}
            </div>
          )}
          <p>
            Are you sure you want to delete banner <strong>{bannerId}</strong>?
          </p>
        </Modal.Body>
        <Modal.Footer>
          <button className="btn btn-default" onClick={() => setShowModal(false)} disabled={deleting}>
            Cancel
          </button>
          <button className="btn btn-danger" onClick={handleDelete} disabled={deleting}>
            {deleting ? 'Deleting…' : 'Delete'}
          </button>
        </Modal.Footer>
      </Modal>
    </>
  );
}
