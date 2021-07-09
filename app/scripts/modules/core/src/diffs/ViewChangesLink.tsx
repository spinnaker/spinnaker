import * as React from 'react';

import { ChangesModal } from './ChangesModal';
import { ICommit } from './CommitHistory';
import { IJarDiff } from './JarDiffs';
import { IBuildDiffInfo, ICreationMetadata, ICreationMetadataTag, IExecution, IExecutionStage } from '../domain';
import { LabeledValue, showModal, useData } from '../presentation';
import { ReactInjector } from '../reactShims';

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

export const ViewChangesLink = ({ changeConfig, linkText, nameItem, viewType }: IViewChangesLinkProps) => {
  const changeConfigValue = changeConfig?.metadata?.value || ({} as ICreationMetadata);

  const fetchExecution = () => {
    const isExecution = changeConfigValue.executionType === 'pipeline';
    if (isExecution) {
      return ReactInjector.executionService.getExecution(changeConfigValue.executionId);
    }
    /** A noop promise so `useData` can be utilized */
    return (Promise.resolve({}) as unknown) as PromiseLike<IExecution>;
  };

  const { result: executionDetails, status } = useData(fetchExecution, {} as IExecution, [
    changeConfigValue.executionId,
    changeConfigValue.stageId,
  ]);

  const stage = (executionDetails.stages || []).find((s: IExecutionStage) => s.id === changeConfigValue.stageId);
  const commits = stage?.context?.commits || changeConfig.commits || [];
  const jarDiffs = stage?.context?.jarDiffs || changeConfig.jarDiffs;
  const buildInfo = stage
    ? {
        ...changeConfig.buildInfo,
        ...stage.context.buildInfo,
      }
    : changeConfig.buildInfo;

  const isLoaded = status === 'RESOLVED';
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
