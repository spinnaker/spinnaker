// Copyright 2026 Wise
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import { UIRouterReact } from '@uirouter/react';
import React from 'react';

import { EnablePipelineModal } from './EnablePipelineModal';
import type { IPipeline } from '../../../../domain';
import { setDirectRouter } from '../../../../navigation/directRouter';
import { ReactModal } from '../../../../presentation';

describe('EnablePipelineModal', () => {
  let router: UIRouterReact;

  beforeEach(() => {
    router = new UIRouterReact();
    setDirectRouter(router);
  });

  afterEach(() => {
    ReactModal.dismissAll('test-cleanup');
    setDirectRouter(null);
    router.dispose();
  });

  it('renders a single modal dialog when shown via ReactModal.show', async () => {
    const pipeline = { name: 'my-pipeline', disabled: true } as IPipeline;

    ReactModal.show(EnablePipelineModal, { pipeline } as any, { animation: false }).catch(() => {});

    // Allow the portal to render into document.body
    await new Promise((resolve) => setTimeout(resolve, 0));

    // ReactModal.show already wraps the component in a react-bootstrap <Modal>.
    // The component must NOT render its own <Modal>, otherwise two nested modals
    // are created, which react-overlays 0.9.x aria-hides and Chrome then blocks,
    // freezing the page. There must be exactly one modal dialog in the document.
    expect(document.querySelectorAll('.modal').length).toBe(1);
    expect(document.body.textContent).toContain('Really Enable Pipeline?');
  });
});
