/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.security.authz.file;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.security.authz.AbstractEditRoleProviderCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.LocalFileConverter;
import com.netflix.spinnaker.halyard.config.model.v1.security.FileRoleProvider;
import com.netflix.spinnaker.halyard.config.model.v1.security.GroupMembership;
import com.netflix.spinnaker.halyard.config.model.v1.security.RoleProvider;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@Parameters(separators = "=")
public class EditFileRoleProviderCommand extends AbstractEditRoleProviderCommand<FileRoleProvider> {
  GroupMembership.RoleProviderType roleProviderType = GroupMembership.RoleProviderType.FILE;

  @Parameter(
      names = "--file-path",
      converter = LocalFileConverter.class,
      description = "A path to a file describing the roles of each user.")
  private String filePath;

  @Override
  protected RoleProvider editRoleProvider(FileRoleProvider roleProvider) {
    roleProvider.setPath(isSet(filePath) ? filePath : roleProvider.getPath());
    return roleProvider;
  }
}
