import * as React from 'react';

import { CommitHistory, ICommit } from './CommitHistory';
import { IJarDiff, JarDiffs } from './JarDiffs';
import { IBuildDiffInfo, IJenkinsInfo } from '../domain';
import { IModalComponentProps, ModalBody, ModalFooter, ModalHeader } from '../presentation';

export interface IChangesModalProps extends IModalComponentProps {
  buildInfo: IBuildDiffInfo;
  commits: ICommit[];
  jarDiffs: IJarDiff;
  nameItem: { name: string };
}

const buildJenkinsLink = (jenkins: IJenkinsInfo, build: string): string =>
  build ? `${jenkins.host}job/${jenkins.name}/${build}` : undefined;

export const ChangesModal = ({ buildInfo, commits, dismissModal, jarDiffs, nameItem }: IChangesModalProps) => {
  const previousBuildLink = buildJenkinsLink(buildInfo.jenkins, buildInfo.ancestor);
  const currentBuildLink = buildJenkinsLink(buildInfo.jenkins, buildInfo.target);
  const hasJarChanges = Object.keys(jarDiffs).some((key: string) => jarDiffs[key].length > 0);

  return (
    <>
      <ModalHeader>{`Changes to ${nameItem.name}`}</ModalHeader>
      <ModalBody>
        <div className="flex-container-v flex-1 sp-margin-s-top">
          <div>
            {buildInfo?.ancestor && (
              <div className="col-md-6">
                <strong>Previous: </strong>
                {previousBuildLink ? (
                  <a href={previousBuildLink} target="_blank">{`Build: #${buildInfo.ancestor}`}</a>
                ) : (
                  <span>{`Build: #${buildInfo.ancestor}`}</span>
                )}
              </div>
            )}
            {buildInfo?.target && (
              <div className="col-md-6">
                <strong>Current: </strong>
                {currentBuildLink ? (
                  <a href={currentBuildLink} target="_blank">{`Build: #${buildInfo.target}`}</a>
                ) : (
                  <span>{`Build: #${buildInfo.target}`}</span>
                )}
              </div>
            )}
          </div>
          {Boolean(commits?.length) && (
            <div>
              <div className="component-heading sticky-header">
                <h4>Commits</h4>
              </div>
              <div className="component-body">
                <CommitHistory commits={commits} />
              </div>
            </div>
          )}
          {hasJarChanges && (
            <div>
              <div className="component-heading sticky-header">
                <h4>JAR Changes</h4>
              </div>
              <div className="component-body">
                <JarDiffs jarDiffs={jarDiffs} />
              </div>
            </div>
          )}
        </div>
      </ModalBody>
      <ModalFooter
        primaryActions={
          <button className="btn btn-primary" onClick={dismissModal}>
            Close
          </button>
        }
      />
    </>
  );
};
