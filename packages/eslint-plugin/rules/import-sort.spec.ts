import rule from './import-sort';
import ruleTester from '../utils/ruleTester';

ruleTester.run('import-sort', rule, {
  valid: [
    {
      code: `
import angular from 'angular';
import 'jquery';
import React, { useCallback, useState } from 'react';
import * as Select from 'react-select';

import { LabeledValueList as LabeledValueL, SomeThingElse } from '@spinnaker/core';

import Bar from './bar';
import Baz from '../../../test/baz';

import 'bootstrap.less';
import './styles.less';
      `,
    },
  ],
  invalid: [
    {
      code: "import React, {useState, useCallback} from 'react';",
      output: "import React, { useCallback, useState } from 'react';",
      errors: ['Sort the import statements'],
    },
    {
      code: `
import React, {useState, useCallback} from 'react';
import angular from 'angular';
import * as Select from 'react-select';
      `,
      output: `
import angular from 'angular';
import React, { useCallback, useState } from 'react';
import * as Select from 'react-select';
      `,
      errors: ['Sort the import statements'],
    },
    {
      code: `
import React  from 'react';

const {useState, useCallback} = React;

import { Application } from 'core/application';
import Bar from "./bar";
import angular from 'angular';
// Some comment about react-select
import * as Select from 'react-select';
import {
  LabeledValueList as LabeledValueL,
  SomeThingElse,
} from "@spinnaker/core";
import './styles.less';
import Baz from "../../../test/baz";

import 'jquery';
import 'bootstrap.less';
      `,
      // For some strange reason eslint fixer writes additional newline characters
      output: `
import angular from 'angular';
import 'jquery';
import React from 'react';
// Some comment about react-select
import * as Select from 'react-select';

import { LabeledValueList as LabeledValueL, SomeThingElse } from '@spinnaker/core';
import { Application } from 'core/application';

import Bar from './bar';
import Baz from '../../../test/baz';

import 'bootstrap.less';
import './styles.less';

const {useState, useCallback} = React;
      `,
      errors: ['Sort the import statements'],
    },
  ],
});
