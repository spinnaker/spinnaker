import React from 'react';
import { Creatable } from 'react-select';
import TetherComponent from 'react-tether';

/* from https://github.com/JedWatson/react-select/issues/810#issuecomment-250274937 **/
export class TetheredCreatable extends Creatable {
  constructor(props: any) {
    super(props);
    (this as any).renderOuter = this._renderOuter;
  }

  public _renderOuter() {
    // @ts-ignore - type definitions don't expose renderOuter...
    const menu = super.render.apply(this, arguments);

    // Don't return an updated menu render if we don't have one
    if (!menu) {
      return null;
    }

    /* this.wrapper comes from the ref of the main Select component (super.render()) **/
    const selectWidth = (this as any).wrapper ? (this as any).wrapper.offsetWidth : null;

    return (
      <TetherComponent
        classes={{ element: 'layer-high' }}
        attachment="top left"
        targetAttachment="top left"
        constraints={[{ to: 'window', attachment: 'together', pin: ['top'] }]}
      >
        {/* Apply position:static to our menu so that its parent will get the correct dimensions and we can tether the parent */}
        <div />
        {React.cloneElement(menu, { style: { position: 'static', width: selectWidth } })}
      </TetherComponent>
    );
  }
}
