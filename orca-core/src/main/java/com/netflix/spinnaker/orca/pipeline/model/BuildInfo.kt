package com.netflix.spinnaker.orca.pipeline.model

abstract class BuildInfo<A>(open val name: String?,
                        open val number: Int,
                        open val url: String?,
                        open val result: String?,
                        open val artifacts: List<A>? = emptyList(),
                        open val scm: List<SourceControl>? = emptyList(),
                        open val building: Boolean = false) {
    var fullDisplayName: String? = null
        get() = field ?: "$name#$number"
}
