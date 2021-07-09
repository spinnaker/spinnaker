import { isEmpty } from 'lodash';
import React from 'react';
import { Modal } from 'react-bootstrap';

import { DiffSummary } from './DiffSummary';
import { DiffView } from './DiffView';
import { IPipeline } from '../../../../domain';
import { ModalClose } from '../../../../modal';
import { FormField, IModalComponentProps, ReactSelectInput, useData } from '../../../../presentation';
import { PipelineConfigService } from '../../services/PipelineConfigService';
import { IJsonDiff, JsonUtils, timestamp } from '../../../../utils';
import { Spinner } from '../../../../widgets';

import './showHistory.less';

export interface IShowHistoryModalProps extends IModalComponentProps {
  currentConfig: IPipeline;
  isStrategy: boolean;
  pipelineConfigId: string;
}

interface IHistoryContents {
  timestamp: string;
  contents: string;
  json: IPipeline;
  index: number;
}

const emptyDiff: IJsonDiff = {
  changeBlocks: [],
  details: [],
  summary: {
    additions: 0,
    removals: 0,
    total: 0,
    unchanged: 0,
  },
};

export function ShowPipelineHistoryModal(props: IShowHistoryModalProps) {
  const compareOptions = ['previous version', 'current'];
  const [compareTo, setCompareTo] = React.useState<string>(compareOptions[0]);
  const [diff, setDiff] = React.useState<IJsonDiff>(emptyDiff);
  const [version, setVersion] = React.useState<number>(0);
  const { closeModal, currentConfig, dismissModal, isStrategy, pipelineConfigId } = props;

  const { result: history, status, error } = useData(
    () => {
      return PipelineConfigService.getHistory(pipelineConfigId, isStrategy, 100).then(historyLoaded);
    },
    [],
    [],
  );

  React.useEffect(() => updateDiff(history, version, compareTo), [history, compareTo, version]);

  function historyLoaded(loadedPipelines: IPipeline[]): IHistoryContents[] {
    const historyToBeProcessed = currentConfig ? [currentConfig].concat(loadedPipelines) : loadedPipelines;
    const pipelineHistory: IHistoryContents[] = historyToBeProcessed.map((h, index) => {
      const ts = h.updateTs;
      delete h.updateTs;
      return {
        timestamp: timestamp(ts),
        contents: JSON.stringify(h, null, 2),
        json: h,
        index: index,
      };
    });
    pipelineHistory[0].timestamp += ' (current)';
    if (currentConfig) {
      pipelineHistory[0].timestamp = 'Current config (not saved)';
      pipelineHistory[1].timestamp += ' (last saved)';
    }
    return pipelineHistory;
  }

  function restoreVersion(): void {
    closeModal(history[version].json);
  }

  function updateDiff(h: IHistoryContents[], v: number, c: string): void {
    if (h.length > 0) {
      const right = h[v].contents;
      let left;
      if (c === compareOptions[1]) {
        left = h[0].contents;
      } else {
        left = right;
        if (v < h.length - 1) {
          left = h[v + 1].contents;
        }
      }
      setDiff(JsonUtils.diff(left, right, true));
    }
  }

  return (
    <>
      <Modal key="modal" dialogClassName="modal-lg modal-fullscreen" show={true} onHide={() => {}}>
        <ModalClose dismiss={dismissModal} />
        <Modal.Header>
          <Modal.Title>Pipeline Revision History</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {status === 'PENDING' && (
            <div className="horizontal center">
              <div className="horizontal center">
                <Spinner size="medium" />
              </div>
            </div>
          )}
          {status === 'REJECTED' && (
            <div className="horizontal center">
              <h4 className="text-center">There was an error loading the history for this pipeline.</h4>
              <span>{error}</span>
            </div>
          )}
          {status === 'RESOLVED' && history.length <= 0 && (
            <div className="horizontal center">
              <h4 className="text-center">Sorry, we couldn't find any history for this pipeline.</h4>
            </div>
          )}
          {status === 'RESOLVED' && history.length > 0 && (
            <>
              <div className="history-header row horizontal">
                <div className="col-md-4">
                  <FormField
                    label="Revision"
                    layout={({ label, input }) => (
                      <div className="flex-container-h baseline margin-between-lg">
                        <div className="bold">{label}</div>
                        <div className="flex-grow">{input}</div>
                      </div>
                    )}
                    input={(inputProps) => (
                      <ReactSelectInput
                        clearable={false}
                        options={history.map((h) => ({ label: h.timestamp, value: h.index }))}
                        {...inputProps}
                      />
                    )}
                    onChange={(e) => setVersion(e.target.value)}
                    value={version}
                  />
                </div>
                <div className="diff-section col-md-4 horizontal middle">
                  <DiffSummary summary={diff.summary} />
                  {version > 0 && (
                    <button className="btn btn-sm btn-primary" onClick={restoreVersion}>
                      Restore this version
                    </button>
                  )}
                </div>
                <div className="col-md-4">
                  <FormField
                    label="compare to"
                    input={(inputProps) => (
                      <ReactSelectInput clearable={false} stringOptions={compareOptions} {...inputProps} />
                    )}
                    onChange={(e) => setCompareTo(e.target.value)}
                    value={compareTo}
                  />
                </div>
              </div>
              <form role="form" className="form-horizontal show-history">
                <div className="form-group">
                  {!isEmpty(history) && (
                    <div className="diff-view flex-fill">
                      <DiffView diff={diff} />
                    </div>
                  )}
                </div>
              </form>
            </>
          )}
        </Modal.Body>
        <Modal.Footer>
          <button className="btn btn-default" onClick={dismissModal} type="button">
            Close
          </button>
        </Modal.Footer>
      </Modal>
    </>
  );
}
