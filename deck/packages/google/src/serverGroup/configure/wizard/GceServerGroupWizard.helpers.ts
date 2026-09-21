import type {
  GceServerGroupLocationMode,
  IGceServerGroupCommand,
  IGceServerGroupCommandValidationErrors,
  IPersistedReference,
} from './GceServerGroupWizard.types';

export function getGceServerGroupLocationMode(
  command: Pick<IGceServerGroupCommand, 'regional'>,
): GceServerGroupLocationMode {
  return command.regional ? 'regional' : 'zonal';
}

export function preservePersistedReference<T>(
  options: readonly T[],
  persistedValue: string | null | undefined,
  getValue: (option: T) => string,
  createOption: (value: string) => T,
): Array<IPersistedReference<T>> {
  const references = options.map((value) => ({ value, unresolved: false }));
  if (persistedValue && !options.some((option) => getValue(option) === persistedValue)) {
    references.push({ value: createOption(persistedValue), unresolved: true });
  }
  return references;
}

export function validateGceServerGroupCommand(command: IGceServerGroupCommand): IGceServerGroupCommandValidationErrors {
  const errors: IGceServerGroupCommandValidationErrors = {};
  if (!command.application?.trim()) {
    errors.application = 'Application required.';
  }
  if (!command.credentials?.trim()) {
    errors.credentials = 'Account required.';
  }
  if (!command.region?.trim()) {
    errors.region = 'Region required.';
  }
  if (!command.regional && !command.zone?.trim()) {
    errors.zone = 'Zone required.';
  }
  if (command.regional && command.selectZones && !command.distributionPolicy?.zones?.length) {
    errors.distributionPolicy = { zones: 'At least one zone required.' };
  }
  if (!command.viewState.disableImageSelection && !command.image?.trim()) {
    errors.image = 'Image required.';
  }
  if (command.capacity?.desired === null || command.capacity?.desired === undefined) {
    errors.capacity = { desired: 'Desired capacity required.' };
  }
  return errors;
}
