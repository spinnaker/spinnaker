package com.netflix.spinnaker.keel.diff

interface ResourceDiff<T : Any> {
  val desired: T
  val current: T?
  val affectedRootPropertyTypes: List<Class<*>>
  val affectedRootPropertyNames: Set<String>
  fun hasChanges(): Boolean
  fun toDeltaJson(): Map<String, Any?>
  fun toUpdateJson(): Map<String, Any?>
  fun toDebug(): String
  fun T?.toMap(): Map<String, Any?>?
}
