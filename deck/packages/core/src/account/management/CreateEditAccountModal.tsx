import React, { useEffect, useState } from 'react';
import { Modal } from 'react-bootstrap';

import type { IAccountDefinition } from './AccountManagementService';
import { AccountManagementService } from './AccountManagementService';
import { ACCOUNT_SAMPLES, buildSampleDefinition } from './accountSamples';
import { CloudProviderRegistry } from '../../cloudProvider/CloudProviderRegistry';

const VALID_NAME_RE = /^[a-zA-Z0-9._-]+$/;

export interface ICreateEditAccountModalProps {
  /** When provided the modal is in edit mode and the definition is pre-populated. */
  existing?: IAccountDefinition;
  /** Pre-fills the "type" discriminator when creating a new account. */
  defaultType?: string;
  onClose: () => void;
  onSaved: (account: IAccountDefinition) => void;
}

/**
 * Validates the structure of an account definition document. Returns an error
 * message, or null when the definition is structurally sound. Field-level
 * semantics remain provider-specific and are validated server-side.
 */
export function validateDefinitionStructure(json: string, existing?: IAccountDefinition): string | null {
  let value: any;
  try {
    value = JSON.parse(json);
  } catch (e: any) {
    return `Invalid JSON: ${e.message}`;
  }
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return 'Account definition must be a JSON object';
  }
  if (typeof value.type !== 'string' || !value.type.trim()) {
    return 'Account definition requires a non-empty "type" field';
  }
  if (typeof value.name !== 'string' || !value.name.trim()) {
    return 'Account definition requires a non-empty "name" field';
  }
  if (!VALID_NAME_RE.test(value.name)) {
    return 'Account name may only contain letters, numbers, periods, hyphens and underscores';
  }
  if (existing && value.name !== existing.name) {
    return 'The account name cannot be changed after creation';
  }
  return null;
}

export function CreateEditAccountModal({ existing, defaultType, onClose, onSaved }: ICreateEditAccountModalProps) {
  const isEdit = !!existing;

  const [type, setType] = useState(existing?.type ?? defaultType ?? 'kubernetes');
  const [name, setName] = useState(existing?.name ?? '');
  const [json, setJson] = useState(() =>
    existing ? JSON.stringify(existing, null, 2) : buildSampleDefinition(type, ''),
  );
  // Once the JSON has been hand-edited, changing type/name no longer regenerates it
  const [jsonDirty, setJsonDirty] = useState(isEdit);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const typeTrimmed = type.trim();
  const nameTrimmed = name.trim();
  const sample = ACCOUNT_SAMPLES[typeTrimmed];

  const typeSuggestions = Array.from(
    new Set([...Object.keys(ACCOUNT_SAMPLES), ...CloudProviderRegistry.listRegisteredProviders()]),
  ).sort();

  useEffect(() => {
    if (!isEdit && !jsonDirty) {
      setJson(buildSampleDefinition(typeTrimmed, nameTrimmed));
    }
  }, [isEdit, jsonDirty, typeTrimmed, nameTrimmed]);

  const nameInvalid = nameTrimmed.length > 0 && !VALID_NAME_RE.test(nameTrimmed);
  const structureError = validateDefinitionStructure(json, existing);
  const isSubmittable = !!typeTrimmed && !!nameTrimmed && !nameInvalid && !structureError && !saving;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!isSubmittable) return;
    setError(null);
    setSaving(true);

    // In create mode the helper fields are authoritative for type/name
    const parsed: IAccountDefinition = JSON.parse(json);
    const definition = isEdit ? parsed : { ...parsed, type: typeTrimmed, name: nameTrimmed };

    try {
      const saved = isEdit
        ? await AccountManagementService.updateAccount(definition)
        : await AccountManagementService.createAccount(definition);
      onSaved(saved);
    } catch (err: any) {
      setError(err?.data?.message ?? err?.message ?? 'Failed to save account');
      setSaving(false);
    }
  };

  const resetToSample = () => {
    setJson(buildSampleDefinition(typeTrimmed, nameTrimmed));
    setJsonDirty(false);
  };

  return (
    <Modal show onHide={onClose} bsSize="large">
      <Modal.Header closeButton>
        <Modal.Title>{isEdit ? `Edit Account: ${existing.name}` : 'Create Account'}</Modal.Title>
      </Modal.Header>
      <form onSubmit={handleSubmit}>
        <Modal.Body>
          {error && (
            <div className="alert alert-danger" role="alert">
              {error}
            </div>
          )}

          <div className="row">
            <div className="col-md-6">
              <div className="form-group">
                <label htmlFor="account-type-field">Account Type *</label>
                <input
                  id="account-type-field"
                  className="form-control"
                  type="text"
                  list="account-type-field-suggestions"
                  value={type}
                  onChange={(e) => setType(e.target.value)}
                  disabled={isEdit}
                  autoFocus={!isEdit}
                  required
                  placeholder="e.g. kubernetes"
                />
                <datalist id="account-type-field-suggestions">
                  {typeSuggestions.map((suggestion) => (
                    <option key={suggestion} value={suggestion} />
                  ))}
                </datalist>
                <span className="help-block text-muted">
                  {sample?.description ??
                    (isEdit ? 'Type cannot be changed after creation' : 'No sample available for this type')}
                </span>
              </div>
            </div>
            <div className="col-md-6">
              <div className={`form-group${nameInvalid ? ' has-error' : ''}`}>
                <label htmlFor="account-name-field">Account Name *</label>
                <input
                  id="account-name-field"
                  className="form-control"
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  disabled={isEdit}
                  required
                  placeholder="e.g. my-prod-account"
                />
                <span className={`help-block${nameInvalid ? '' : ' text-muted'}`}>
                  {nameInvalid
                    ? 'Name may only contain letters, numbers, periods, hyphens and underscores'
                    : isEdit
                    ? 'Name cannot be changed after creation'
                    : 'Unique across all accounts'}
                </span>
              </div>
            </div>
          </div>

          <div className={`form-group${jsonDirty && structureError ? ' has-error' : ''}`}>
            <label htmlFor="account-definition-json" style={{ width: '100%' }}>
              Account Definition (JSON) *
              {!isEdit && sample && (
                <button type="button" className="btn btn-link btn-xs pull-right" onClick={resetToSample}>
                  Reset to {typeTrimmed} sample
                </button>
              )}
            </label>
            <textarea
              id="account-definition-json"
              className="form-control"
              style={{ fontFamily: 'monospace' }}
              rows={16}
              value={json}
              onChange={(e) => {
                setJson(e.target.value);
                setJsonDirty(true);
              }}
              spellCheck={false}
              required
            />
            {jsonDirty && structureError ? (
              <span className="help-block">{structureError}</span>
            ) : (
              <span className="help-block text-muted">
                Fields other than <code>type</code> and <code>name</code> are specific to the account type; replace the
                placeholder values before saving. Secret values may use Spinnaker secret URIs (e.g.{' '}
                <code>encrypted:&lt;engine&gt;!&lt;parameters&gt;</code>) instead of plaintext.
              </span>
            )}
          </div>
        </Modal.Body>
        <Modal.Footer>
          <button type="button" className="btn btn-default" onClick={onClose} disabled={saving}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={!isSubmittable}>
            {saving ? 'Saving…' : isEdit ? 'Update Account' : 'Create Account'}
          </button>
        </Modal.Footer>
      </form>
    </Modal>
  );
}
