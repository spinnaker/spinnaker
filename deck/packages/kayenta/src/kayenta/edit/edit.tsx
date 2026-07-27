import { UIView } from '@uirouter/react';
import * as React from 'react';

import ConfigList from './configList';
import Footer from './footer';
import ListDetail from '../layout/listDetail';

/*
 * Component for editing canary configurations for an application.
 */
export default function CanaryConfigEdit() {
  const List = <ConfigList />;
  // TODO: need to break these down by groups
  const noWrap = { wrap: false };
  const Detail = <UIView {...noWrap} name="detail" />;
  return (
    <div className="vertical flex-1">
      <ListDetail list={List} detail={Detail} className="flex-1" detailClass="vertical flex-1" />
      <Footer>
        <UIView {...noWrap} name="footer" />
      </Footer>
    </div>
  );
}
