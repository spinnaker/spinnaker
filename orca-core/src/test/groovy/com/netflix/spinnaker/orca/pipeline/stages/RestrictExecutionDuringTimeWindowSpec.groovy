/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.stages
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.batch.StageStatusPropagationListener
import com.netflix.spinnaker.orca.batch.TaskTaskletAdapter
import com.netflix.spinnaker.orca.batch.lifecycle.AbstractBatchLifecycleSpec
import com.netflix.spinnaker.orca.config.JesqueConfiguration
import com.netflix.spinnaker.orca.config.OrcaConfiguration
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.test.redis.EmbeddedRedisConfiguration
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ContextConfiguration
import spock.lang.Unroll

import java.text.SimpleDateFormat

import static com.netflix.spinnaker.orca.batch.PipelineInitializerTasklet.initializationStep
import static com.netflix.spinnaker.orca.pipeline.stages.RestrictExecutionDuringTimeWindow.SuspendExecutionDuringTimeWindowTask
import static com.netflix.spinnaker.orca.pipeline.stages.RestrictExecutionDuringTimeWindow.SuspendExecutionDuringTimeWindowTask.HourMinute
import static com.netflix.spinnaker.orca.pipeline.stages.RestrictExecutionDuringTimeWindow.SuspendExecutionDuringTimeWindowTask.TimeWindow
/**
 *
 * @author sthadeshwar
 */
@Unroll
@ContextConfiguration(classes = [EmbeddedRedisConfiguration, JesqueConfiguration, OrcaConfiguration])
class RestrictExecutionDuringTimeWindowSpec extends AbstractBatchLifecycleSpec {

  @Autowired ApplicationContext applicationContext;
  def listeners = [new StageStatusPropagationListener(executionRepository)]
  def task = Mock(Task)

  def setup() {
    TimeZone.'default' = TimeZone.getTimeZone("America/Los_Angeles")
    System.setProperty("user.timezone", "America/Los_Angeles")
  }

  def "pipeline should inject a stage before if current stage context has restrictExecutionDuringWindow set to true"() {
    expect:
    pipeline.stages.size() == 2
  }

  void 'stage should be scheduled at #expectedTime when triggered at #scheduledTime with time windows #timeWindows'() {
    when:
    Date result = SuspendExecutionDuringTimeWindowTask.calculateScheduledTime(scheduledTime, timeWindows)

    then:
    result.equals(expectedTime)

    where:
    scheduledTime           | expectedTime            | timeWindows

    date("02/14 01:00:00")  | date("02/14 01:00:00")  | [window(hourMinute("22:00"), hourMinute("05:00"))]

    date("02/13 21:45:00")  | date("02/14 06:00:00")  | [window(hourMinute("06:00"), hourMinute("10:00"))]

    date("02/13 13:45:00")  | date("02/13 22:00:00")  | [window(hourMinute("22:00"), hourMinute("05:00"))]
    date("02/13 06:30:00")  | date("02/13 10:00:00")  | [window(hourMinute("10:00"), hourMinute("13:00"))]
    date("02/13 09:59:59")  | date("02/13 10:00:00")  | [window(hourMinute("10:00"), hourMinute("13:00"))]
    date("02/13 10:00:00")  | date("02/13 10:00:00")  | [window(hourMinute("10:00"), hourMinute("13:00"))]
    date("02/13 10:00:35")  | date("02/13 10:00:35")  | [window(hourMinute("10:00"), hourMinute("13:00"))]
    date("02/13 10:01:35")  | date("02/13 10:01:35")  | [window(hourMinute("10:00"), hourMinute("13:00"))]

    date("02/13 09:59:59")  | date("02/13 10:00:00")  | [window(hourMinute("10:00"), hourMinute("13:00")), window(hourMinute("16:00"), hourMinute("18:00"))]
    date("02/13 10:00:00")  | date("02/13 10:00:00")  | [window(hourMinute("10:00"), hourMinute("13:00")), window(hourMinute("16:00"), hourMinute("18:00"))]
    date("02/13 10:01:35")  | date("02/13 10:01:35")  | [window(hourMinute("10:00"), hourMinute("13:00")), window(hourMinute("16:00"), hourMinute("18:00"))]
    date("02/13 10:01:35")  | date("02/13 10:01:35")  | [window(hourMinute("16:00"), hourMinute("18:00")), window(hourMinute("10:00"), hourMinute("13:00"))]

    date("02/13 14:30:00")  | date("02/13 14:30:00")  | [window(hourMinute("13:00"), hourMinute("18:00"))]

    date("02/13 00:00:00")  | date("02/13 10:00:00")  | [window(hourMinute("10:00"), hourMinute("13:00"))]
    date("02/13 00:01:00")  | date("02/13 10:00:00")  | [window(hourMinute("10:00"), hourMinute("13:00"))]

    date("02/13 00:01:00")  | date("02/13 15:00:00")  | [window(hourMinute("15:00"), hourMinute("18:00"))]
    date("02/13 11:01:00")  | date("02/13 15:00:00")  | [window(hourMinute("15:00"), hourMinute("18:00"))]

    date("02/13 14:01:00")  | date("02/14 13:00:00")  | [window(hourMinute("13:00"), hourMinute("14:00"))]

    date("02/13 22:00:00")  | date("02/13 22:00:00")  | [window(hourMinute("22:00"), hourMinute("05:00"))]

    date("02/13 01:00:00")  | date("02/13 01:00:00")  | [window(hourMinute("00:00"), hourMinute("05:00")), window(hourMinute("22:00"), hourMinute("23:59"))]

    date("02/13 00:59:59")  | date("02/13 10:00:00")  | [window(hourMinute("10:00"), hourMinute("11:00")), window(hourMinute("13:00"), hourMinute("14:00")),
                                                         window(hourMinute("15:00"), hourMinute("16:00"))]
    date("02/13 10:30:59")  | date("02/13 10:30:59")  | [window(hourMinute("10:00"), hourMinute("11:00")), window(hourMinute("13:00"), hourMinute("14:00")),
                                                         window(hourMinute("15:00"), hourMinute("16:00"))]
    date("02/13 12:30:59")  | date("02/13 13:00:00")  | [window(hourMinute("10:00"), hourMinute("11:00")), window(hourMinute("13:00"), hourMinute("14:00")),
                                                         window(hourMinute("15:00"), hourMinute("16:00"))]
    date("02/13 16:00:00")  | date("02/13 16:00:00")  | [window(hourMinute("10:00"), hourMinute("11:00")), window(hourMinute("13:00"), hourMinute("14:00")),
                                                         window(hourMinute("15:00"), hourMinute("16:00"))]
    date("02/13 16:01:00")  | date("02/14 10:00:00")  | [window(hourMinute("10:00"), hourMinute("11:00")), window(hourMinute("13:00"), hourMinute("14:00")),
                                                         window(hourMinute("15:00"), hourMinute("16:00"))]
  }

  void 'stage should be scheduled at #expectedTime when triggered at #scheduledTime with time windows #stage in stage context'() {
    when:
    Date result = SuspendExecutionDuringTimeWindowTask.getTimeInWindow(stage, scheduledTime)

    then:
    result.equals(expectedTime)

    where:
    scheduledTime           | expectedTime            | stage

    date("02/13 06:30:00")  | date("02/13 10:00:00")  | stage([window("10:00", "13:00")])
    date("02/13 09:59:59")  | date("02/13 10:00:00")  | stage([window("10:00", "13:00")])
    date("02/13 10:00:00")  | date("02/13 10:00:00")  | stage([window("10:00", "13:00")])
    date("02/13 10:00:35")  | date("02/13 10:00:35")  | stage([window("10:00", "13:00")])
    date("02/13 10:01:35")  | date("02/13 10:01:35")  | stage([window("10:00", "13:00")])

    date("02/13 09:59:59")  | date("02/13 10:00:00")  | stage([window("10:00", "13:00"), window("16:00", "18:00")])
    date("02/13 10:00:00")  | date("02/13 10:00:00")  | stage([window("10:00", "13:00"), window("16:00", "18:00")])
    date("02/13 10:01:35")  | date("02/13 10:01:35")  | stage([window("10:00", "13:00"), window("16:00", "18:00")])
    date("02/13 10:01:35")  | date("02/13 10:01:35")  | stage([window("16:00", "18:00"), window("10:00", "13:00")])

    date("02/13 14:30:00")  | date("02/13 14:30:00")  | stage([window("13:00", "18:00")])

    date("02/13 00:00:00")  | date("02/13 10:00:00")  | stage([window("10:00", "13:00")])
    date("02/13 00:01:00")  | date("02/13 10:00:00")  | stage([window("10:00", "13:00")])

    date("02/13 00:01:00")  | date("02/13 15:00:00")  | stage([window("15:00", "18:00")])
    date("02/13 11:01:00")  | date("02/13 15:00:00")  | stage([window("15:00", "18:00")])

    date("02/13 14:01:00")  | date("02/14 13:00:00")  | stage([window("13:00", "14:00")])

    date("02/13 13:45:00")  | date("02/13 22:00:00")  | stage([window("22:00", "05:00")])
    date("02/13 22:00:00")  | date("02/13 22:00:00")  | stage([window("22:00", "05:00")])
    date("02/14 01:00:00")  | date("02/14 01:00:00")  | stage([window("22:00", "05:00")])

    date("02/13 05:01:00")  | date("02/13 22:00:00")  | stage([window("22:00", "05:00")])
    date("02/13 01:00:00")  | date("02/13 01:00:00")  | stage([window("00:00", "05:00"), window("22:00", "23:59")])

    date("02/13 00:59:59")  | date("02/13 10:00:00")  | stage([window("10:00", "11:00"), window("13:00", "14:00"), window("15:00", "16:00")])
    date("02/13 10:30:59")  | date("02/13 10:30:59")  | stage([window("10:00", "11:00"), window("13:00", "14:00"), window("15:00", "16:00")])
    date("02/13 12:30:59")  | date("02/13 13:00:00")  | stage([window("10:00", "11:00"), window("13:00", "14:00"), window("15:00", "16:00")])  //*
    date("02/13 16:00:00")  | date("02/13 16:00:00")  | stage([window("10:00", "11:00"), window("13:00", "14:00"), window("15:00", "16:00")])
    date("02/13 16:01:00")  | date("02/14 10:00:00")  | stage([window("10:00", "11:00"), window("13:00", "14:00"), window("15:00", "16:00")]) //*
  }

//  def "pipline should be STOPPED if restrictExecutionDuringWindow is set to true and current time is not within window"() {
//
//  }

  // helper methods
  // ------------------------------------------------------------------------------------------------------------------

  @Override
  Pipeline createPipeline() {
    Pipeline.builder().withStages([[name: "stage2", type:"stage2", restrictExecutionDuringTimeWindow: true]]).build()
  }

  @Override
  protected Job configureJob(JobBuilder jobBuilder) {
    def stage = pipeline.namedStage("stage2")
    def builder = jobBuilder.flow(initializationStep(steps, pipeline))
    def stageBuilder = new InjectStageBuilder(applicationContext, steps, new TaskTaskletAdapter(executionRepository, []))
    stageBuilder.build(builder, stage).build().build()
  }

  private class InjectStageBuilder extends LinearStage {
    InjectStageBuilder(ApplicationContext applicationContext, StepBuilderFactory steps, TaskTaskletAdapter adapter) {
      super("stage2")
      setApplicationContext(applicationContext)
      setTaskListeners(listeners)
      setSteps(steps)
      setTaskTaskletAdapter(adapter)
    }

    @Override
    public List<Step> buildSteps(Stage stage) {
      [buildStep(stage, "step", task)]
    }
  }

  private hourMinute(String hourMinuteStr) {
    int hour = hourMinuteStr.tokenize(":").get(0) as Integer
    int min = hourMinuteStr.tokenize(":").get(1) as Integer
    return new HourMinute(hour, min)
  }

  private Date date(String dateStr) {
    SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm:ss z yyyy");
    sdf.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"));
    return sdf.parse(dateStr + " PST 2015")
  }

  private TimeWindow window(HourMinute start, HourMinute end) {
    return new TimeWindow(start, end)
  }

  private Map window(String start, String end) {
    int startHour = start.tokenize(":").get(0) as Integer
    int startMin = start.tokenize(":").get(1) as Integer
    int endHour = end.tokenize(":").get(0) as Integer
    int endMin = end.tokenize(":").get(1) as Integer
    return [startHour: startHour, startMin: startMin, endHour: endHour, endMin: endMin]
  }

  private Stage stage(List<Map> windows) {
    Map restrictedExecutionWindow = [whitelist:windows]
    Map context = [restrictedExecutionWindow: restrictedExecutionWindow]
    Pipeline pipeline = new Pipeline()
    return new PipelineStage(pipeline, "testRestrictExecution", context)
  }

}
