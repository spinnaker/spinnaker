/*
 * Copyright 2018 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.security.authn.iap;

import com.netflix.spinnaker.halyard.cli.command.v1.config.security.authn.AuthnMethodCommand;
import com.netflix.spinnaker.halyard.config.model.v1.security.AuthnMethod;
import com.netflix.spinnaker.halyard.config.model.v1.security.AuthnMethod.Method;

public class IAPCommand extends AuthnMethodCommand {
  public AuthnMethod.Method getMethod() {
    return Method.IAP;
  }

  public IAPCommand() {
    super();
    registerSubcommand(new EditIAPCommand());
  }
}
