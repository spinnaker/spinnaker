package com.netflix.spinnaker.hamkrest

import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isA
import com.natpryce.hamkrest.should.shouldMatch
import com.natpryce.hamkrest.throws
import kotlin.reflect.KClass

infix fun <T> T.shouldEqual(expected: T) {
  shouldMatch(equalTo(expected))
}

@Suppress("UNCHECKED_CAST")
inline infix fun <reified T : Throwable> (() -> Any?).shouldThrow(matcher: Matcher<T>) {
  (this as () -> Unit) shouldMatch throws(matcher)
}

@Suppress("UNCHECKED_CAST")
inline infix fun <reified T : Throwable> (() -> Any?).shouldThrow(@Suppress("UNUSED_PARAMETER") type: KClass<T>) {
  (this as () -> Unit) shouldMatch throws(isA<T>())
}
