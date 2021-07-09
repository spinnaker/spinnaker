import classNames from 'classnames';
import React from 'react';

import { EditAwsCodeBuildSecondarySourceVersionModal } from './EditAwsCodeBuildSecondarySourceVersionModal';
import { EditAwsCodeBuildSourceModal } from './EditAwsCodeBuildSourceModal';
import { IAwsCodeBuildSecondarySourcesVersion, IAwsCodeBuildSource } from './IAwsCodeBuildSource';
import { ArtifactIcon } from '../../../../artifact';
import { IPipeline, IStage } from '../../../../domain';
import { IFormInputProps } from '../../../../index';
import { createFakeReactSyntheticEvent } from '../../../../presentation';

export interface IAwsCodeBuildSourceListProps {
  stage: IStage;
  pipeline: IPipeline;
  sources: IAwsCodeBuildSource[];
  updateSources: (notifications: IAwsCodeBuildSource[]) => void;
}

export interface IAwsCodeBuildSecondarySourceVersionListProps extends IFormInputProps {
  stage: IStage;
  pipeline: IPipeline;
}

export class AwsCodeBuildSecondarySourcesVersionList extends React.Component<
  IAwsCodeBuildSecondarySourceVersionListProps
> {
  constructor(props: IAwsCodeBuildSecondarySourceVersionListProps) {
    super(props);
  }

  private addSecondarySourcesVersion = () => {
    this.editSecondarySourceVersion();
  };

  private onChange(value: any) {
    this.props.onChange(createFakeReactSyntheticEvent({ value, name: this.props.name }));
  }

  private editSecondarySourceVersion = (source?: IAwsCodeBuildSecondarySourcesVersion, index?: number) => {
    const { value, stage, pipeline } = this.props;
    const secondarySourcesVersionOverride = source ?? {};
    EditAwsCodeBuildSecondarySourceVersionModal.show({ secondarySourcesVersionOverride, stage, pipeline })
      .then((newSecondarySource) => {
        const secondarySource = value || [];
        const secondarySourceCopy = [...secondarySource];
        if (!source) {
          secondarySourceCopy.push(newSecondarySource);
        } else {
          secondarySourceCopy[index] = newSecondarySource;
        }
        this.onChange(secondarySourceCopy);
      })
      .catch(() => {});
  };

  private removeSource = (index: number) => {
    const secondarySourcesVersionOverride = [...this.props.value];
    secondarySourcesVersionOverride.splice(index, 1);
    this.onChange(secondarySourcesVersionOverride);
  };

  public render() {
    const value = this.props.value as IAwsCodeBuildSecondarySourcesVersion[];
    return (
      <div className="row">
        <div className={'col-md-12'}>
          <table className="table table-condensed">
            <thead>
              <tr>
                <th>Source Identifier</th>
                <th>Source Version</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {value &&
                value.map((secondarySourceVersion, i) => {
                  return (
                    <tr key={i} className={classNames({ 'templated-pipeline-item': false })}>
                      <td>{secondarySourceVersion.sourceIdentifier}</td>
                      <td>{secondarySourceVersion.sourceVersion}</td>
                      <td>
                        <button
                          className="btn btn-xs btn-link"
                          onClick={() => this.editSecondarySourceVersion(secondarySourceVersion, i)}
                        >
                          Edit
                        </button>
                        <button className="btn btn-xs btn-link" onClick={() => this.removeSource(i)}>
                          Remove
                        </button>
                      </td>
                    </tr>
                  );
                })}
            </tbody>
            <tfoot>
              <tr>
                <td colSpan={7}>
                  <button className="btn btn-block add-new" onClick={() => this.addSecondarySourcesVersion()}>
                    <span className="glyphicon glyphicon-plus-sign" /> Add Secondary Sources Version Override
                  </button>
                </td>
              </tr>
            </tfoot>
          </table>
        </div>
      </div>
    );
  }
}

export class AwsCodeBuildSourceList extends React.Component<IAwsCodeBuildSourceListProps> {
  private addSource = () => {
    this.editSource();
  };

  private editSource = (buildSource?: IAwsCodeBuildSource, index?: number) => {
    const { sources, updateSources, stage, pipeline } = this.props;
    const source = buildSource ?? {};
    EditAwsCodeBuildSourceModal.show({ source, stage, pipeline })
      .then((newSource) => {
        const sourceCopy = sources || [];
        if (!source) {
          updateSources(sourceCopy.concat(newSource));
        } else {
          const update = [...sourceCopy];
          update[index] = newSource;
          updateSources(update);
        }
      })
      .catch(() => {});
  };

  private removeSource = (index: number) => {
    const sources = [...this.props.sources];
    sources.splice(index, 1);
    this.props.updateSources(sources);
  };

  public render() {
    const { sources } = this.props;
    return (
      <div className="row">
        <div className={'col-md-12'}>
          <table className="table table-condensed">
            <thead>
              <tr>
                <th>Artifact</th>
                <th>Type</th>
                <th>Source Version</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {sources &&
                sources.map((source, i) => {
                  return (
                    <tr key={i} className={classNames({ 'templated-pipeline-item': false })}>
                      <td>
                        <ArtifactIcon type={source.sourceArtifact.artifactType} width="16" height="16" />
                        {source.sourceArtifact.artifactDisplayName}
                      </td>
                      <td>{source.type}</td>
                      <td>{source.sourceVersion}</td>
                      <td>
                        <button className="btn btn-xs btn-link" onClick={() => this.editSource(source, i)}>
                          Edit
                        </button>
                        <button className="btn btn-xs btn-link" onClick={() => this.removeSource(i)}>
                          Remove
                        </button>
                      </td>
                    </tr>
                  );
                })}
            </tbody>
            <tfoot>
              <tr>
                <td colSpan={7}>
                  <button className="btn btn-block add-new" onClick={() => this.addSource()}>
                    <span className="glyphicon glyphicon-plus-sign" /> Add Secondary Source
                  </button>
                </td>
              </tr>
            </tfoot>
          </table>
        </div>
      </div>
    );
  }
}
