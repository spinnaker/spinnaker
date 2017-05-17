import * as React from 'react';
import autoBindMethods from 'class-autobind-decorator';

import { timestamp, NgReact } from '@spinnaker/core';

import { Property } from '../../domain/property.domain';
import { IPropertyHistoryEntry } from '../../domain/propertyHistory.domain';
import { NetflixReactInjector } from 'netflix/react.injector';

import './FastPropertyHistory.less';

interface IProps {
  property: Property;
}

interface IState {
  loading: boolean;
  loadError: boolean;
  entries: IPropertyHistoryEntry[];
  diffsExpanded: number[];
}

@autoBindMethods
export class FastPropertyHistory extends React.Component<IProps, IState> {

  constructor(props: IProps) {
    super(props);
    this.state = {
      loading: true,
      loadError: false,
      entries: [],
      diffsExpanded: [],
    };
  }

  public componentDidMount(): void {
    this.loadHistory();
  }

  public componentWillReceiveProps(): void {
    this.loadHistory();
  }

  private loadHistory(): void {
    this.setState({loading: true});
    const property = this.props.property;
    NetflixReactInjector.fastPropertyReader.getHistory(property.propertyId, property.env)
      .then(entries => this.setState({entries, loading: false, loadError: false}))
      .catch(() => this.setState({loading: false, loadError: true}));
  }

  private showDiff(e: React.MouseEvent<HTMLElement>) {
    const index = parseInt((e.target as HTMLElement).getAttribute('data-index'), 10);
    this.setState({diffsExpanded: this.state.diffsExpanded.concat(index)})
  }

  private hideDiff(e: React.MouseEvent<HTMLElement>) {
    const index = parseInt((e.target as HTMLElement).getAttribute('data-index'), 10);
    this.setState({diffsExpanded: this.state.diffsExpanded.filter(i => i !== index)});
  }

  public render() {
    const rows = [] as JSX.Element[];
    const { DiffView } = NgReact;
    this.state.entries.forEach((entry, index) => {
      const diffExpanded = this.state.diffsExpanded.includes(index);
      rows.push((
        <tr key={index}>
          <td>{timestamp(entry.timestamp)}</td>
          <td>{entry.comment}</td>
          <td>{entry.sourceOfUpdate}</td>
          <td>{entry.updatedBy}</td>
          <td>{entry.cmc}</td>
          <td>
            {!entry.diff && <span>n/a</span>}
            {entry.diff && !diffExpanded && <a className="clickable" data-index={index} onClick={this.showDiff}>{'Show Diff'}</a>}
            {entry.diff && diffExpanded && <a className="clickable" data-index={index} onClick={this.hideDiff}>{'Hide Diff'}</a>}
          </td>
        </tr>
      ));
      if (diffExpanded) {
        rows.push((
          <tr key={index + 'diff'}>
            <td colSpan={6} className="row-diff-details">
              <DiffView diff={entry.diff}/>
            </td>
          </tr>
        ));
      }
    });

    return (
      <div>
        <table className="table table-condensed table-fast-property-history">
          <thead>
            <tr>
              <th width="20%">Timestamp</th>
              <th width="25%">Comment</th>
              <th width="10%">Source</th>
              <th width="15%">Updated By</th>
              <th width="10%">CMC</th>
              <th width="10%">Diff</th>
            </tr>
          </thead>
          <tbody>
            {this.state.loading && (
              <tr><td colSpan={6}><div className="text-center">loading...</div></td></tr>
            )}
            {!this.state.loading && rows}
            {!this.state.loading && rows.length === 0 && (
              <tr><td colSpan={6}><div className="text-center">No history found for this property</div></td></tr>
            )}
          </tbody>
        </table>
        {this.state.loadError && (
          <div className="text-center">Could not load history for this property.</div>
        )}
      </div>
    );
  }
}
