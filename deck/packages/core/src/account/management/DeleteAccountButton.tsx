import React, { useState } from 'react';
import { Modal } from 'react-bootstrap';

import { AccountManagementService } from './AccountManagementService';

export interface IDeleteAccountButtonProps {
  accountName: string;
  onDeleted: () => void;
}

export function DeleteAccountButton({ accountName, onDeleted }: IDeleteAccountButtonProps) {
  const [showModal, setShowModal] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleDelete = () => {
    setDeleting(true);
    setError(null);
    AccountManagementService.deleteAccount(accountName)
      .then(() => {
        setShowModal(false);
        onDeleted();
      })
      .catch((e: any) => {
        setDeleting(false);
        setError(e?.data?.message ?? 'Failed to delete account. Please try again.');
      });
  };

  return (
    <>
      <button className="btn btn-default btn-xs" onClick={() => setShowModal(true)}>
        Delete
      </button>

      <Modal show={showModal} onHide={() => !deleting && setShowModal(false)} bsSize="small">
        <Modal.Header closeButton={!deleting}>
          <Modal.Title>Delete account</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {error && (
            <div className="alert alert-danger" role="alert" style={{ marginBottom: 12 }}>
              {error}
            </div>
          )}
          <p>
            Are you sure you want to delete account <strong>{accountName}</strong>?
          </p>
          <p className="text-muted">
            Resources deployed with this account may become unmanageable until an account with the same name is
            recreated.
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
