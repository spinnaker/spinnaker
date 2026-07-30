/*
 * Copyright 2025 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.google.provider.agent.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.HttpHeaders
import com.google.api.services.compute.model.ForwardingRule
import com.google.api.services.compute.model.ForwardingRuleList
import com.netflix.spinnaker.clouddriver.google.provider.agent.FailureLogger
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.slf4j.LoggerFactory

/**
 * Helper class providing base implementations for ForwardingRule batch callbacks.
 * Eliminates duplication across load balancer caching agents by extracting common callback patterns.
 */
@Slf4j
@CompileStatic
abstract class ForwardingRuleCallbackHelper {

  /**
   * Factory method to create a singleton callback for processing a single forwarding rule.
   */
  JsonBatchCallback<ForwardingRule> newForwardingRuleSingletonCallback() {
    return new ForwardingRuleSingletonCallback()
  }

  /**
   * Factory method to create a list callback for processing multiple forwarding rules.
   */
  JsonBatchCallback<ForwardingRuleList> newForwardingRuleListCallback() {
    return new ForwardingRuleListCallback()
  }

  /**
   * Template method for determining if a forwarding rule should be processed by this agent.
   * Each agent implements its own filtering logic (e.g., checking for target proxies, backend services, schemes).
   *
   * @param forwardingRule The forwarding rule to evaluate
   * @return true if this agent should process the rule, false otherwise
   */
  protected abstract boolean shouldProcessForwardingRule(ForwardingRule forwardingRule)

  /**
   * Template method for processing a forwarding rule after it passes the filter.
   * Typically creates a load balancer object and queues the next batch request.
   *
   * @param forwardingRule The forwarding rule to process
   */
  protected abstract void processForwardingRule(ForwardingRule forwardingRule)

  /**
   * Error message to use when a forwarding rule fails the filter in on-demand mode.
   */
  protected abstract String getFilterErrorMessage()

  /**
   * Callback for processing a single forwarding rule (on-demand caching).
   * Handles 404 errors gracefully and validates the rule before processing.
   */
  class ForwardingRuleSingletonCallback extends JsonBatchCallback<ForwardingRule> {

    @Override
    void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
      // 404 is thrown if the forwarding rule does not exist in the given region. Any other exception needs to be propagated.
      if (e.code != 404) {
        def errorJson = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(e)
        log.error errorJson
      }
    }

    @Override
    void onSuccess(ForwardingRule forwardingRule, HttpHeaders responseHeaders) throws IOException {
      if (shouldProcessForwardingRule(forwardingRule)) {
        processForwardingRule(forwardingRule)
      } else {
        throw new IllegalArgumentException(getFilterErrorMessage())
      }
    }
  }

  /**
   * Callback for processing a list of forwarding rules (bulk caching).
   * Filters and processes each rule that matches the agent's criteria.
   */
  class ForwardingRuleListCallback extends JsonBatchCallback<ForwardingRuleList> implements FailureLogger {

    @Override
    void onSuccess(ForwardingRuleList forwardingRuleList, HttpHeaders responseHeaders) throws IOException {
      forwardingRuleList?.items?.each { ForwardingRule forwardingRule ->
        if (shouldProcessForwardingRule(forwardingRule)) {
          processForwardingRule(forwardingRule)
        }
      }
    }

    @Override
    void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
      LoggerFactory.getLogger(this.class).error e.getMessage()
    }
  }
}
