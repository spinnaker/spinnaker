/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.kayenta.judge

import org.scalatest.{BeforeAndAfterAll, Suite}
import org.springframework.core.annotation.{AnnotatedElementUtils, AnnotationAttributes}
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.support.DirtiesContextTestExecutionListener
import org.springframework.test.context.{TestContext, TestContextManager}
import org.springframework.util.Assert

/**
  * Manages Spring test contexts via a TestContextManager.
  *
  * Implemented as a stackable trait that uses beforeAll() and afterAll() hooks to invoke initialization
  * and destruction logic, respectively.
  * Test contexts are marked dirty, and hence cleaned up, after all test methods have executed.
  * There is currently no support for indicating that a test method dirties a context.
  *
  * Sample usage:
  * {{{
  *   @ContextConfiguration(classes = Array(classOf[SomeConfiguration]))
  *   class SomeTestSpec extends FlatSpec with TestContextManagement {
  *
  *      // Use standard Autowired Spring annotation to inject necessary dependencies
  *      // Note that Spring will inject val (read-only) fields
  *      @Autowired
  *      val someDependency: SomeClass = _
  *
  *      "Some test" should "verify something" in {
  *        // Test implementation that uses injected dependency
  *      }
  *
  *   }
  * }}}
  *
  * @see org.springframework.test.context.TestContextManager
  *
  */
trait TestContextManagement extends BeforeAndAfterAll { this: Suite =>

  private val testContextManager: TestContextManager = new TestContextManager(this.getClass)

  abstract override def beforeAll(): Unit = {
    super.beforeAll
    testContextManager.registerTestExecutionListeners(AlwaysDirtiesContextTestExecutionListener)
    testContextManager.beforeTestClass
    testContextManager.prepareTestInstance(this)
  }

  abstract override def afterAll(): Unit = {
    testContextManager.afterTestClass
    super.afterAll
  }
}

/**
  * Test execution listener that always dirties the context to ensure that contexts get cleaned after test execution.
  *
  * Note that this class dirties the context after all test methods have run.
  */
protected object AlwaysDirtiesContextTestExecutionListener extends DirtiesContextTestExecutionListener {

  @throws(classOf[Exception])
  override def afterTestClass(testContext: TestContext) {
    val testClass: Class[_] = testContext.getTestClass
    Assert.notNull(testClass, "The test class of the supplied TestContext must not be null")

    val annotationType: String = classOf[DirtiesContext].getName
    val annAttrs: AnnotationAttributes = AnnotatedElementUtils.getMergedAnnotationAttributes(testClass, annotationType)
    val hierarchyMode: DirtiesContext.HierarchyMode = if ((annAttrs == null)) null else annAttrs.getEnum[DirtiesContext.HierarchyMode]("hierarchyMode")
    dirtyContext(testContext, hierarchyMode)
  }
}
