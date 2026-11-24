import { pick } from 'lodash';

import type { IFormikStageConfigInjectedProps } from '@spinnaker/core';

import type { IManifestSelector } from './IManifestSelector';

const MANIFEST_SELECTOR_FIELDS: Array<keyof IManifestSelector> = [
  'account',
  'app',
  'cluster',
  'criteria',
  'kind',
  'kinds',
  'labelSelectors',
  'location',
  'manifestName',
  'mode',
];

// ManifestSelector reports a copy of the selector after a mode change, so Formik stage forms
// must store the reported fields instead of relying on the selector editing the stage in place.
export function applyManifestSelector(
  formik: IFormikStageConfigInjectedProps['formik'],
  selector: IManifestSelector,
): void {
  const changes: Partial<IManifestSelector> = pick(selector, MANIFEST_SELECTOR_FIELDS);
  const values = formik.values as Partial<IManifestSelector>;
  if (Object.keys(changes).some((key: keyof IManifestSelector) => values[key] !== changes[key])) {
    formik.setValues({ ...formik.values, ...changes });
  }
}
