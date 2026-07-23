import React from 'react';
import Select from 'react-select';

import { TetheredSelect } from './TetheredSelect';

describe('TetheredSelect', () => {
  [false, true].forEach((multi) => {
    it(`anchors the ${multi ? 'multi' : 'single'} select menu below the control at the control width`, () => {
      spyOn(Select.prototype as any, 'renderOuter').and.returnValue(<div className="Select-menu-outer" />);
      const select = new TetheredSelect({ multi, options: [] } as any);
      (select as any).wrapper = { offsetWidth: 320 };

      const rendered = (select as any)._renderOuter() as React.ReactElement;
      const [target, menu] = React.Children.toArray(rendered.props.children) as React.ReactElement[];

      expect(target.props.style).toEqual({ position: 'absolute', top: '100%', left: 0, width: '100%' });
      expect(menu.props.style).toEqual({ position: 'static', width: 320 });
    });
  });
});
