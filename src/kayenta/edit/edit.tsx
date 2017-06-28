import * as React from 'react';
import ListDetail from './listDetail';
import ConfigList from './configList';
import MetricList from './metricList';

/*
 * Component for editing all available settings in a single canary configuration.
 */
export default function CanaryConfigEdit() {
  const List = <ConfigList/>;
  // TODO: need to break these down by groups
  const Detail = <MetricList/>;
  return <ListDetail list={List} detail={Detail}/>;
}
