/*
 * Copyright 2019 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.google.deploy.validators;

import static org.assertj.core.api.Java6Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidationErrors;
import com.netflix.spinnaker.clouddriver.google.deploy.description.SetStatefulDiskDescription;
import com.netflix.spinnaker.clouddriver.google.security.FakeGoogleCredentials;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import org.assertj.core.api.Condition;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

@RunWith(JUnit4.class)
public class SetStatefulDiskDescriptionValidatorTest {

  private static final String ACCOUNT_NAME = "spintest";
  private static final String REGION = "us-central1";
  private GoogleNamedAccountCredentials CREDENTIALS =
      new GoogleNamedAccountCredentials.Builder()
          .name(ACCOUNT_NAME)
          .credentials(new FakeGoogleCredentials())
          .regionToZonesMap(ImmutableMap.of(REGION, ImmutableList.of("us-central1-b")))
          .build();

  private SetStatefulDiskDescriptionValidator validator;

  @Before
  public void setUp() {
    validator = new SetStatefulDiskDescriptionValidator();
  }

  @Test
  public void testNoErrors() {
    SetStatefulDiskDescription description = new SetStatefulDiskDescription();
    description.setCredentials(CREDENTIALS);
    description.setRegion(REGION);
    description.setServerGroupName("testapp-v000");
    description.setDeviceName("testapp-v000-1");

    DescriptionValidationErrors errors = new DescriptionValidationErrors(description);

    validator.validate(ImmutableList.of(), description, errors);

    assertThat(errors.hasErrors()).isFalse();
  }

  @Test
  public void testNoFields() {
    SetStatefulDiskDescription description = new SetStatefulDiskDescription();

    DescriptionValidationErrors errors = new DescriptionValidationErrors(description);

    validator.validate(ImmutableList.of(), description, errors);

    assertThat(errors.getAllErrors()).hasSize(3);
    assertThat(errors.getAllErrors()).haveAtLeastOne(errorOnField("region"));
    assertThat(errors.getAllErrors()).haveAtLeastOne(errorOnField("serverGroupName"));
    assertThat(errors.getAllErrors()).haveAtLeastOne(errorOnField("deviceName"));
  }

  @Test
  public void testInvalidRegion() {
    SetStatefulDiskDescription description = new SetStatefulDiskDescription();
    description.setCredentials(CREDENTIALS);
    description.setRegion("some-unknown-region");
    description.setServerGroupName("testapp-v000");
    description.setDeviceName("testapp-v000-1");

    DescriptionValidationErrors errors = new DescriptionValidationErrors(description);

    validator.validate(ImmutableList.of(), description, errors);

    assertThat(errors.getAllErrors()).hasSize(1);
    assertThat(errors.getAllErrors()).haveAtLeastOne(errorOnField("region"));
  }

  private Condition<ObjectError> errorOnField(String field) {
    return new Condition<>(
        e -> e instanceof FieldError && ((FieldError) e).getField().equals(field),
        "has field named " + field);
  }
}
