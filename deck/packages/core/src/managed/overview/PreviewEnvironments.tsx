import React from 'react';

import { EnvironmentOverview } from './EnvironmentOverview';
import type { OrderedEnvironments } from '../environmentBaseElements/EnvironmentsRender';
import { EnvironmentsRender } from '../environmentBaseElements/EnvironmentsRender';
import type { QueryEnvironment } from './types';
import { getDocsUrl } from '../utils/defaults';
import { useLogEvent } from '../utils/logging';

interface IPreviewEnvironmentsProps {
  orderedEnvironments: OrderedEnvironments<QueryEnvironment>;
  isConfigured?: boolean;
}

export const PreviewEnvironments = ({ orderedEnvironments, isConfigured }: IPreviewEnvironmentsProps) => {
  const { environments: previewEnvironments, ...previewEnvironmentsProps } = orderedEnvironments;
  const logClick = useLogEvent('PreviewEnvironments');
  const docsLink = getDocsUrl('previewEnvironments');
  const hasActivePreviewEnvironments = previewEnvironments.length > 0;

  return (
    <>
      {(isConfigured || hasActivePreviewEnvironments) && (
        <h4 className="sp-margin-2xl-top sp-margin-m-bottom self-left">
          <b>Preview Environments</b>
        </h4>
      )}
      {isConfigured && !hasActivePreviewEnvironments && (
        <div className="self-left">
          No PRs matching your branch filter were found.
          {docsLink && (
            <a target="_blank" onClick={() => logClick({ action: 'Learn More' })} href={docsLink}>
              Learn More
            </a>
          )}
        </div>
      )}
      <EnvironmentsRender {...previewEnvironmentsProps}>
        {previewEnvironments.map((env) => (
          <EnvironmentOverview key={env.name} environment={env} />
        ))}
      </EnvironmentsRender>
    </>
  );
};
