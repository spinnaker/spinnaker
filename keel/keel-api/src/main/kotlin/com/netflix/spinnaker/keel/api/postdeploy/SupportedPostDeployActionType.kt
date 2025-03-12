package com.netflix.spinnaker.keel.api.postdeploy

/**
 * Used to register subtypes of post deploy actions for serialization
 */
data class SupportedPostDeployActionType<T: PostDeployAction>(
  val name: String,
  val type: Class<T>
)

inline fun <reified T : PostDeployAction> SupportedPostDeployActionType(
  name: String
): SupportedPostDeployActionType<T> =
  SupportedPostDeployActionType(name, T::class.java)
