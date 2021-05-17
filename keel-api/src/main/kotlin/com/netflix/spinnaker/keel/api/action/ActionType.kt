package com.netflix.spinnaker.keel.api.action

/**
 * Types of actions.
 *
 * An action has a state, a status, and metadata about the action.
 */
enum class ActionType {
  VERIFICATION, POST_DEPLOY
}
