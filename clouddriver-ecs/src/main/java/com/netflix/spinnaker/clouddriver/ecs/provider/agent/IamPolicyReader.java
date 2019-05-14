/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.provider.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IamPolicyReader {
  private static final Logger logger = LoggerFactory.getLogger(IamPolicyReader.class);

  ObjectMapper mapper;

  public IamPolicyReader(ObjectMapper objectMapper) {
    this.mapper = objectMapper;
  }

  public Set<IamTrustRelationship> getTrustedEntities(String urlEncodedPolicyDocument) {
    Set<IamTrustRelationship> trustedEntities = Sets.newHashSet();

    String decodedPolicyDocument = URLDecoder.decode(urlEncodedPolicyDocument);

    Map<String, Object> policyDocument;
    try {
      policyDocument = mapper.readValue(decodedPolicyDocument, Map.class);
      List<Map<String, Object>> statementItems =
          (List<Map<String, Object>>) policyDocument.get("Statement");
      for (Map<String, Object> statementItem : statementItems) {
        if ("sts:AssumeRole".equals(statementItem.get("Action"))) {
          Map<String, Object> principal = (Map<String, Object>) statementItem.get("Principal");

          for (Map.Entry<String, Object> principalEntry : principal.entrySet()) {
            if (principalEntry.getValue() instanceof List) {
              ((List) principalEntry.getValue())
                  .stream()
                      .forEach(
                          o ->
                              trustedEntities.add(
                                  new IamTrustRelationship(principalEntry.getKey(), o.toString())));
            } else {
              trustedEntities.add(
                  new IamTrustRelationship(
                      principalEntry.getKey(), principalEntry.getValue().toString()));
            }
          }
        }
      }
    } catch (IOException e) {
      logger.error(
          "Unable to extract trusted entities (policyDocument: {})", urlEncodedPolicyDocument, e);
    }

    return trustedEntities;
  }
}
