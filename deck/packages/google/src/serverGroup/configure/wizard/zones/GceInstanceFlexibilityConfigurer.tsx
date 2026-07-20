import { isEmpty } from 'lodash';
import React from 'react';

import type { IGceInstanceFlexibilityPolicy, IGceInstanceSelection } from '../../../../domain/serverGroup';

export interface IGceInstanceFlexibilityConfigurerProps {
  instanceFlexibilityPolicy?: IGceInstanceFlexibilityPolicy;
  regional?: boolean;
  targetShape?: string;
  validationError?: string;
  setInstanceFlexibilityPolicy: (policy: IGceInstanceFlexibilityPolicy) => void;
}

const FLEX_TARGET_SHAPES = new Set(['BALANCED', 'ANY', 'ANY_SINGLE_ZONE']);
const VALIDATION_ERROR_ID = 'gce-instance-flexibility-error';

function hasSelections(policy?: IGceInstanceFlexibilityPolicy): boolean {
  return !isEmpty(policy?.instanceSelections);
}

function emptySelection(): IGceInstanceSelection {
  return { machineTypes: [''] };
}

/** First unused selection-N name so rename/remove cannot overwrite an existing entry. */
export function nextSelectionName(existingNames: string[]): string {
  const used = new Set(existingNames);
  let index = 1;
  while (used.has(`selection-${index}`)) {
    index += 1;
  }
  return `selection-${index}`;
}

function hasNonEmptyMachineTypes(selection?: IGceInstanceSelection): boolean {
  const machineTypes = selection?.machineTypes || [];
  return machineTypes.length > 0 && machineTypes.every((machineType) => !!machineType && machineType.trim().length > 0);
}

function normalizeMachineType(machineType?: string | null): string | undefined {
  const trimmed = machineType?.trim();
  if (!trimmed) {
    return undefined;
  }
  return trimmed.substring(trimmed.lastIndexOf('/') + 1).toLowerCase();
}

// GCP requires each normalized machine type to occur only once across the entire policy.
function hasUniqueMachineTypes(policy?: IGceInstanceFlexibilityPolicy): boolean {
  const machineTypes = new Set<string>();
  for (const selection of Object.values(policy?.instanceSelections || {})) {
    for (const machineType of selection?.machineTypes || []) {
      const normalizedMachineType = normalizeMachineType(machineType);
      if (normalizedMachineType && machineTypes.has(normalizedMachineType)) {
        return false;
      }
      if (normalizedMachineType) {
        machineTypes.add(normalizedMachineType);
      }
    }
  }
  return true;
}

function hasValidRank(selection?: IGceInstanceSelection): boolean {
  return (
    selection?.rank == null ||
    (Number.isFinite(selection.rank) && Number.isInteger(selection.rank) && selection.rank >= 0)
  );
}

export function GceInstanceFlexibilityConfigurer({
  instanceFlexibilityPolicy,
  regional,
  targetShape: targetShapeProp,
  validationError,
  setInstanceFlexibilityPolicy,
}: IGceInstanceFlexibilityConfigurerProps) {
  const policy = instanceFlexibilityPolicy || { instanceSelections: {} };
  const entries = Object.entries(policy.instanceSelections || {});
  const enabled = entries.length > 0;
  const targetShape = (targetShapeProp || '').trim().toUpperCase();
  const regionalRequired = enabled && !regional;
  const evenBlocked = enabled && (!targetShape || targetShape === 'EVEN' || !FLEX_TARGET_SHAPES.has(targetShape));
  const duplicateMachineTypes = enabled && !hasUniqueMachineTypes(policy);

  const updateSelections = (instanceSelections: { [name: string]: IGceInstanceSelection }) => {
    setInstanceFlexibilityPolicy({ instanceSelections });
  };

  const addSelection = () => {
    const nextName = nextSelectionName(Object.keys(policy.instanceSelections || {}));
    updateSelections({ ...policy.instanceSelections, [nextName]: emptySelection() });
  };

  const removeSelection = (name: string) => {
    const next = { ...policy.instanceSelections };
    delete next[name];
    updateSelections(next);
  };

  const renameSelection = (oldName: string, newName: string): boolean => {
    const trimmed = newName.trim();
    if (!trimmed || trimmed === oldName || policy.instanceSelections[trimmed]) {
      return false;
    }
    const next: { [name: string]: IGceInstanceSelection } = {};
    Object.entries(policy.instanceSelections).forEach(([name, selection]) => {
      next[name === oldName ? trimmed : name] = selection;
    });
    updateSelections(next);
    return true;
  };

  const updateSelection = (name: string, selection: IGceInstanceSelection) => {
    updateSelections({ ...policy.instanceSelections, [name]: selection });
  };

  const updateMachineType = (name: string, index: number, value: string) => {
    const selection = policy.instanceSelections[name];
    const machineTypes = [...(selection.machineTypes || [])];
    machineTypes[index] = value;
    updateSelection(name, { ...selection, machineTypes });
  };

  const addMachineType = (name: string) => {
    const selection = policy.instanceSelections[name];
    updateSelection(name, {
      ...selection,
      machineTypes: [...(selection.machineTypes || []), ''],
    });
  };

  const removeMachineType = (name: string, index: number) => {
    const selection = policy.instanceSelections[name];
    const machineTypes = (selection.machineTypes || []).filter((_type, i) => i !== index);
    updateSelection(name, {
      ...selection,
      machineTypes: machineTypes.length ? machineTypes : [''],
    });
  };

  const updateRank = (name: string, rawRank: string) => {
    const selection = policy.instanceSelections[name];
    if (rawRank === '') {
      const { rank, ...withoutRank } = selection;
      updateSelection(name, withoutRank as IGceInstanceSelection);
      return;
    }
    const parsed = Number(rawRank);
    if (Number.isFinite(parsed) && Number.isInteger(parsed) && parsed >= 0) {
      updateSelection(name, { ...selection, rank: parsed });
    }
  };

  return (
    <div className="form-group">
      <div className="col-md-3 sm-label-right">
        <b>Instance Flexibility</b>
      </div>
      <div className="col-md-9">
        {validationError && (
          <div className="alert alert-danger" id={VALIDATION_ERROR_ID} role="alert">
            {validationError}
          </div>
        )}
        {!enabled && (
          <button type="button" className="btn btn-sm btn-default" onClick={addSelection}>
            Add flexibility policy
          </button>
        )}
        {enabled && (
          <div>
            {regionalRequired && (
              <div className="alert alert-warning">Flexibility requires a regional server group.</div>
            )}
            {evenBlocked && (
              <div className="alert alert-warning">
                Flexibility requires target shape BALANCED, ANY, or ANY_SINGLE_ZONE (not EVEN).
              </div>
            )}
            {duplicateMachineTypes && (
              <div className="alert alert-warning" role="alert">
                Machine types must be unique across instance selections.
              </div>
            )}
            {entries.map(([name, selection]) => (
              <div key={name} className="well well-sm" style={{ marginBottom: '10px' }} data-selection-name={name}>
                {(() => {
                  const selectionId = `instance-flexibility-selection-${encodeURIComponent(name)}`;
                  const rankInvalid = !hasValidRank(selection);
                  return (
                    <>
                      <div className="form-group">
                        <label className="col-md-3 sm-label-right" htmlFor={`${selectionId}-name`}>
                          Selection name
                        </label>
                        <div className="col-md-6">
                          <input
                            id={`${selectionId}-name`}
                            className="form-control input-sm"
                            defaultValue={name}
                            onBlur={(e) => {
                              if (!renameSelection(name, e.currentTarget.value)) {
                                e.currentTarget.value = name;
                              }
                            }}
                          />
                        </div>
                        <div className="col-md-3">
                          <button
                            type="button"
                            className="btn btn-sm btn-default"
                            aria-label={`Remove selection ${name}`}
                            onClick={() => removeSelection(name)}
                          >
                            Remove
                          </button>
                        </div>
                      </div>
                      <div className="form-group">
                        <label className="col-md-3 sm-label-right" htmlFor={`${selectionId}-rank`}>
                          Rank (optional)
                        </label>
                        <div className="col-md-3">
                          <input
                            id={`${selectionId}-rank`}
                            type="number"
                            min={0}
                            step={1}
                            className="form-control input-sm"
                            aria-describedby={rankInvalid && validationError ? VALIDATION_ERROR_ID : undefined}
                            aria-invalid={rankInvalid}
                            value={selection.rank == null ? '' : selection.rank}
                            onChange={(e) => updateRank(name, e.target.value)}
                          />
                        </div>
                      </div>
                      <div className="form-group">
                        <div className="col-md-3 sm-label-right">Machine types</div>
                        <div className="col-md-9">
                          {(selection.machineTypes || []).map((machineType, index) => (
                            <div key={index} style={{ display: 'flex', marginBottom: '4px' }}>
                              <input
                                id={`${selectionId}-machine-type-${index}`}
                                className="form-control input-sm"
                                aria-describedby={
                                  !machineType?.trim() && validationError ? VALIDATION_ERROR_ID : undefined
                                }
                                aria-invalid={!machineType?.trim()}
                                value={machineType}
                                onChange={(e) => updateMachineType(name, index, e.target.value)}
                                placeholder="e.g. n2-standard-8"
                                aria-label={`Machine type ${index + 1} for selection ${name}`}
                              />
                              <button
                                type="button"
                                className="btn btn-sm btn-default"
                                style={{ marginLeft: '4px' }}
                                aria-label={`Remove machine type ${index + 1} from selection ${name}`}
                                onClick={() => removeMachineType(name, index)}
                              >
                                ×
                              </button>
                            </div>
                          ))}
                          <button type="button" className="btn btn-sm btn-link" onClick={() => addMachineType(name)}>
                            Add machine type
                          </button>
                        </div>
                      </div>
                    </>
                  );
                })()}
              </div>
            ))}
            <button type="button" className="btn btn-sm btn-default" onClick={addSelection}>
              Add selection
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

export function hasValidFlexibilityPolicy(command: {
  regional?: boolean;
  distributionPolicy?: { targetShape?: string };
  instanceFlexibilityPolicy?: IGceInstanceFlexibilityPolicy;
}): boolean {
  if (!hasSelections(command.instanceFlexibilityPolicy)) {
    return true;
  }
  if (!command.regional) {
    return false;
  }
  const targetShape = (command.distributionPolicy?.targetShape || '').trim().toUpperCase();
  if (!FLEX_TARGET_SHAPES.has(targetShape)) {
    return false;
  }
  // Clouddriver rejects empty machineTypes and negative ranks; blank placeholders must not pass submit.
  return (
    Object.values(command.instanceFlexibilityPolicy.instanceSelections || {}).every(
      (selection) => hasNonEmptyMachineTypes(selection) && hasValidRank(selection),
    ) && hasUniqueMachineTypes(command.instanceFlexibilityPolicy)
  );
}
