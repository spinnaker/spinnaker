import { flatten, isEmpty } from 'lodash';
import React from 'react';

import type { IExecutionStage } from '@spinnaker/core';
import { Markdown } from '@spinnaker/core';

import type { IManifestNamesByNamespace } from '../ManifestCoordinates';

export interface IDeletedManifestsProps {
  stage: IExecutionStage;
}

// PromoteManifestKatoOutputsTask writes the result under this literal dotted key in the stage context, unfiltered
// (the copy in stage.outputs can be filtered out by configuration), which is why we read it from the context.
export const getDeletedManifests = (stage: IExecutionStage): IManifestNamesByNamespace => {
  return stage.context?.['outputs.manifestNamesByNamespace'];
};

export const DeletedManifests = ({ stage }: IDeletedManifestsProps) => {
  const deleted = getDeletedManifests(stage);
  const namespaces = Object.keys(deleted || {}).filter((namespace) => !isEmpty(deleted[namespace]));

  if (namespaces.length === 0) {
    // Only claim nothing was deleted when the stage succeeded; running, failed or canceled stages show nothing here.
    return stage.status === 'SUCCEEDED' ? (
      <dl>
        <dt>Deleted</dt>
        <dd>No resources deleted</dd>
      </dl>
    ) : null;
  }

  // One line per deleted object, flattened across namespace keys in data order. The stage deletes within its single
  // location namespace (shown in the coordinates), so no namespace headers; cluster-scoped objects may come under an
  // empty or missing key and are listed too.
  const lines = flatten(namespaces.map((namespace) => deleted[namespace]));

  return (
    <dl>
      <dt>Deleted</dt>
      <dd>
        <Markdown message={'```\n' + lines.join('\n') + '\n```'} />
      </dd>
    </dl>
  );
};
