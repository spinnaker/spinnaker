import { get } from 'lodash';
import React from 'react';

import type { IExecutionDetailsSectionProps } from '../common';
import { ExecutionDetailsSection } from '../common';
import { StageFailureMessage } from '../../../details';

/**
 * Format bytes to a human-readable string
 */
function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 Bytes';
  if (!bytes || isNaN(bytes)) return 'N/A';

  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));

  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

export class UploadArtifactToObjectStorageExecutionDetails extends React.Component<IExecutionDetailsSectionProps> {
  public static title = 'uploadArtifactToObjectStorageStatus';

  public render() {
    const { current, name, stage } = this.props;

    const destinationUri: string = get(stage, 'context.destinationUri');
    const fileCount: number = get(stage, 'context.fileCount');
    const totalSizeBytes: number = get(stage, 'context.totalSizeBytes');

    return (
      <ExecutionDetailsSection name={name} current={current}>
        <div className="row">
          <div className="col-md-12">
            <dl className="dl-horizontal dl-narrow">
              {destinationUri && (
                <>
                  <dt>Destination</dt>
                  <dd>
                    <code>{destinationUri}</code>
                  </dd>
                </>
              )}
              {fileCount !== undefined && fileCount !== null && (
                <>
                  <dt>Files</dt>
                  <dd>{fileCount}</dd>
                </>
              )}
              {totalSizeBytes !== undefined && totalSizeBytes !== null && (
                <>
                  <dt>Total Size</dt>
                  <dd>{formatBytes(totalSizeBytes)}</dd>
                </>
              )}
            </dl>
          </div>
        </div>
        {stage.failureMessage && <StageFailureMessage stage={stage} message={stage.failureMessage} />}
      </ExecutionDetailsSection>
    );
  }
}
