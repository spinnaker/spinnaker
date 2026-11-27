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
  public static title = 'uploadArtifactToObjectStorageConfig';

  private getResultObject(): Record<string, any> {
    const katoTasks: any[] = get(this.props.stage, ['context', 'kato.tasks'], []);
    if (katoTasks.length > 0) {
      const resultObjects = katoTasks[katoTasks.length - 1].resultObjects || [];
      return resultObjects.find((r: any) => r.destinationUri) || {};
    }
    return {};
  }

  public render() {
    const { current, name, stage } = this.props;

    const result = this.getResultObject();
    const destinationUri: string = result.destinationUri;
    const fileCount: number = result.fileCount;
    const uploadCount: number | undefined = result.uploadCount;
    const skippedCount: number | undefined = result.skippedCount;
    const totalSizeBytes: number = result.totalSizeBytes;
    const extractArchive: boolean = get(stage, 'context.extractArchive', true);
    const deleteStaleFiles: boolean | undefined = result.deleteStaleFiles;
    const deletedCount: number | undefined = result.deletedCount;

    return (
      <ExecutionDetailsSection name={name} current={current}>
        <div className="row">
          <div className="col-md-9">
            <dl className="dl-narrow dl-horizontal">
              {destinationUri && (
                <>
                  <dt>Destination</dt>
                  <dd>
                    <code>{destinationUri}</code>
                  </dd>
                </>
              )}
              {totalSizeBytes !== undefined && totalSizeBytes !== null && (
                <>
                  <dt>Total Size</dt>
                  <dd>{formatBytes(totalSizeBytes)}</dd>
                </>
              )}
              <>
                <dt>Extract</dt>
                <dd>{extractArchive ? 'Yes' : 'No'}</dd>
              </>
              {deleteStaleFiles !== undefined && deleteStaleFiles !== null && (
                <>
                  <dt>Delete Stale</dt>
                  <dd>{deleteStaleFiles ? 'Yes' : 'No'}</dd>
                </>
              )}
              {deleteStaleFiles && deletedCount !== undefined && deletedCount !== null && (
                <>
                  <dt>Deleted</dt>
                  <dd>{deletedCount}</dd>
                </>
              )}
              {uploadCount !== undefined && uploadCount !== null && (
                <>
                  <dt>Uploaded</dt>
                  <dd>{uploadCount}</dd>
                </>
              )}
              {skippedCount !== undefined && skippedCount !== null && (
                <>
                  <dt>Skipped</dt>
                  <dd>{skippedCount}</dd>
                </>
              )}
              {fileCount !== undefined && fileCount !== null && (
                <>
                  <dt>Total Files</dt>
                  <dd>{fileCount}</dd>
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
