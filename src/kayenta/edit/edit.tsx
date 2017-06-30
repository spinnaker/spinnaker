import * as React from 'react';
import { UIView } from '@uirouter/react';
import ListDetail from './listDetail';
import ConfigList from './configList';

/*
 * Component for editing all available settings in a single canary configuration.
 */
export default function CanaryConfigEdit() {
  const List = <ConfigList/>;
  // TODO: need to break these down by groups
  const Detail = <UIView name="detail"/>;
  return <ListDetail list={List} detail={Detail}/>;
}
