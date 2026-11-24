import * as React from 'react';

import { ChangesModal } from './ChangesModal';
import type { ICommit } from './CommitHistory';
import type { IJarDiff } from './JarDiffs';
import { useDeckRuntimeServices } from '../bootstrap/DeckRuntimeContext';
import type { IBuildDiffInfo, ICreationMetadata, ICreationMetadataTag, IExecution, IExecutionStage } from '../domain';
import { LabeledValue, showModal, useData } from '../presentation';

export interface IViewChangesConfig {
  buildInfo?: IBuildDiffInfo;
  commits?: ICommit[];
  jarDiffs?: IJarDiff;
  metadata?: ICreationMetadataTag;
}

export interface IViewChangesLinkProps {
  changeConfig: IViewChangesConfig;
  linkText?: string;
  nameItem: { name: string };
  viewType?: string;
}

const LOCAL_CHANGE_SOURCE = 'local';

export const ViewChangesLink = ({ changeConfig, linkText, nameItem, viewType }: IViewChangesLinkProps) => {
  const { executionService } = useDeckRuntimeServices();
  const changeConfigValue = changeConfig?.metadata?.value || ({} as ICreationMetadata);
  const executionType = changeConfigValue.executionType || LOCAL_CHANGE_SOURCE;
  const isExecution = executionType === 'pipeline';
  const executionId = isExecution ? changeConfigValue.executionId : LOCAL_CHANGE_SOURCE;
  const stageId = isExecution ? changeConfigValue.stageId : LOCAL_CHANGE_SOURCE;

  const fetchExecution = () => {
    if (isExecution && executionId && stageId) {
      return executionService.getExecution(executionId);
    }
    /** A noop promise so `useData` can be utilized */
    return Promise.resolve({} as IExecution);
  };

  const { result: executionDetails, status } = useData(fetchExecution, {} as IExecution, [
    executionType,
    executionId,
    stageId,
  ]);

  const stage = (executionDetails.stages || []).find((s: IExecutionStage) => s.id === stageId);
  const commits = stage?.context?.commits || changeConfig.commits || [];
  const jarDiffs = stage?.context?.jarDiffs || changeConfig.jarDiffs;
  const buildInfo = stage
    ? {
        ...changeConfig.buildInfo,
        ...stage.context.buildInfo,
      }
    : changeConfig.buildInfo;

  const hasRequiredExecutionMetadata = !isExecution || Boolean(executionId && stageId);
  const isLoaded = status === 'RESOLVED' && hasRequiredExecutionMetadata;
  const hasJarDiffs = Object.keys(jarDiffs || {}).some((key: string) => jarDiffs[key].length > 0);
  const hasChanges = hasJarDiffs || commits.length;

  const showChangesModal = () => {
    const modalProps = {
      buildInfo,
      commits,
      jarDiffs,
      nameItem,
    };
    showModal(ChangesModal, modalProps, { maxWidth: 700 });
  };
  const viewChanges = (
    <a className="clickable" onClick={showChangesModal}>
      {linkText || 'View Changes'}
    </a>
  );

  if (!isLoaded || !hasChanges) {
    return null;
  }

  if (viewType === 'linkOnly') {
    return <span>{viewChanges}</span>;
  }

  return <LabeledValue label="Changes" value={viewChanges} />;
};
