// Copyright 2026 DoorDash, Inc.
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

import { module } from 'angular';

import { ApiTokensPageContainer } from './ApiTokensPageContainer';
import { registerRootState } from '../navigation/rootState.registration';
import type { INestedState } from '../navigation/state.provider';

export const APITOKEN_STATES = 'spinnaker.core.apitoken.states';

module(APITOKEN_STATES, []);

registerRootState((stateConfigProvider) => {
  const apiTokens: INestedState = {
    name: 'apiTokens',
    url: '/api-tokens',
    views: {
      'main@': {
        component: ApiTokensPageContainer,
        $type: 'react',
      },
    },
    data: {
      pageTitleMain: {
        label: 'API Tokens',
      },
    },
  };

  stateConfigProvider.addToRootState(apiTokens);
});
