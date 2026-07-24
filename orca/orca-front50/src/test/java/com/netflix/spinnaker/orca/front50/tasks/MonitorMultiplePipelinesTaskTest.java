package com.netflix.spinnaker.orca.front50.tasks;

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.PipelineTrigger;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MonitorMultiplePipelinesTaskTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  private PipelineExecutionImpl pipeline;

  private ExecutionRepository executionRepository;

  private static final String testApplication = "test_app";
  private static final String testUser = "test_user";

  @BeforeEach
  public void setup() {
    pipeline = new PipelineExecutionImpl(PIPELINE, testApplication);
    pipeline.setAuthentication(new PipelineExecution.AuthenticationDetails(testUser));

    executionRepository = mock(ExecutionRepository.class);
  }

  @Test
  public void shouldMonitorMultiplePipelinesTaskFinishSuccessfully() {
    var task = new MonitorMultiplePipelinesTask(executionRepository, objectMapper);

    var context = new HashMap<String, Object>();
    context.put("levelNumber", 0);
    context.put("orderOfExecutionsSize", 0);
    context.put("executionIds", List.of());

    var stageExecution = new StageExecutionImpl(pipeline, "runMultiplePipelines", context);

    pipeline.getStages().add(stageExecution);

    var result = task.execute(stageExecution);

    assertEquals(ExecutionStatus.SUCCEEDED, result.getStatus());
  }

  @Test
  public void shouldMonitorMultiplePipelinesTaskFinishSuccessfullyWhenDeployManifestAndArtifact() {
    var testPipelineId = "test_pipeline_id";

    var task = new MonitorMultiplePipelinesTask(executionRepository, objectMapper);

    var context = new HashMap<String, Object>();
    context.put("levelNumber", 0);
    context.put("orderOfExecutionsSize", 0);
    context.put("executionId", testPipelineId);

    var stageExecution = new StageExecutionImpl(pipeline, "runJobManifest", context);

    var testParentPipeline = new PipelineExecutionImpl(PIPELINE, testApplication);
    testParentPipeline.setStatus(ExecutionStatus.RUNNING);

    var testPipeline = new PipelineExecutionImpl(PIPELINE, testApplication);
    testPipeline.setStatus(ExecutionStatus.SUCCEEDED);

    var testTrigger = new PipelineTrigger(testParentPipeline);
    testTrigger.getParameters().put("app", "test_app");
    testPipeline.setTrigger(testTrigger);

    when(executionRepository.retrieve(PIPELINE, testPipelineId)).thenReturn(testPipeline);

    var testDeployManifestStage = new StageExecutionImpl(testPipeline, "runJobManifest", context);
    testDeployManifestStage.setName("Deploy");
    testDeployManifestStage.setStatus(ExecutionStatus.SUCCEEDED);
    testDeployManifestStage.setOutputs(
        new HashMap<>() {
          {
            put(
                "manifests",
                List.of(
                    new HashMap<>() {
                      {
                        put(
                            "metadata",
                            new HashMap<>() {
                              {
                                put("name", "test_app");
                              }
                            });
                        put("kind", "DaemonSet");
                      }
                    }));
            put("deploy.account.name", "test_account");
            put(
                "outputs.createdArtifacts",
                List.of(
                    new HashMap<>() {
                      {
                        put("name", "test_artifact");
                        put("location", "us-west2");
                      }
                    }));
          }
        });

    testPipeline.getStages().add(testDeployManifestStage);

    var result = task.execute(stageExecution);

    assertEquals(ExecutionStatus.SUCCEEDED, result.getStatus());
  }

  @Test
  public void shouldMonitorMultiplePipelinesTaskFinishSuccessfullyWhenDeployManifest() {
    var testPipelineId = "test_pipeline_id";

    var task = new MonitorMultiplePipelinesTask(executionRepository, objectMapper);

    var context = new HashMap<String, Object>();
    context.put("levelNumber", 0);
    context.put("orderOfExecutionsSize", 0);
    context.put("executionId", testPipelineId);

    var stageExecution = new StageExecutionImpl(pipeline, "runJobManifest", context);

    var testParentPipeline = new PipelineExecutionImpl(PIPELINE, testApplication);
    testParentPipeline.setStatus(ExecutionStatus.RUNNING);

    var testPipeline = new PipelineExecutionImpl(PIPELINE, testApplication);
    testPipeline.setStatus(ExecutionStatus.SUCCEEDED);

    var testTrigger = new PipelineTrigger(testParentPipeline);
    testTrigger.getParameters().put("app", "test_app");
    testPipeline.setTrigger(testTrigger);

    when(executionRepository.retrieve(PIPELINE, testPipelineId)).thenReturn(testPipeline);

    var testDeployManifestStage1 = new StageExecutionImpl(testPipeline, "runJobManifest", context);
    testDeployManifestStage1.setName("Deploy");
    testDeployManifestStage1.setStatus(ExecutionStatus.SUCCEEDED);
    testDeployManifestStage1.setOutputs(
        new HashMap<>() {
          {
            put(
                "manifests",
                List.of(
                    new HashMap<>() {
                      {
                        put(
                            "metadata",
                            new HashMap<>() {
                              {
                                put("name", "test_app");
                              }
                            });
                        put("kind", "DaemonSet");
                      }
                    }));
          }
        });

    var testDeployManifestStage2 = new StageExecutionImpl(testPipeline, "runJobManifest", context);
    testDeployManifestStage2.setName("Deploy");
    testDeployManifestStage2.setStatus(ExecutionStatus.SUCCEEDED);
    testDeployManifestStage2.setOutputs(
        new HashMap<>() {
          {
            put(
                "manifests",
                List.of(
                    new HashMap<>() {
                      {
                        put(
                            "metadata",
                            new HashMap<>() {
                              {
                                put("name", "test_app");
                              }
                            });
                        put("kind", "Deployment");
                      }
                    }));
          }
        });

    var testDeployManifestStage3 = new StageExecutionImpl(testPipeline, "runJobManifest", context);
    testDeployManifestStage3.setName("Deploy");
    testDeployManifestStage3.setStatus(ExecutionStatus.SUCCEEDED);
    testDeployManifestStage3.setOutputs(
        new HashMap<>() {
          {
            put(
                "manifests",
                List.of(
                    new HashMap<>() {
                      {
                        put(
                            "metadata",
                            new HashMap<>() {
                              {
                                put("name", "test_app");
                              }
                            });
                        put("kind", "StatefulSet");
                      }
                    }));
          }
        });

    var testDeployManifestStage4 = new StageExecutionImpl(testPipeline, "runJobManifest", context);
    testDeployManifestStage4.setName("Deploy");
    testDeployManifestStage4.setStatus(ExecutionStatus.SUCCEEDED);
    testDeployManifestStage4.setOutputs(
        new HashMap<>() {
          {
            put(
                "manifests",
                List.of(
                    new HashMap<>() {
                      {
                        put(
                            "metadata",
                            new HashMap<>() {
                              {
                                put("name", "unknown");
                              }
                            });
                        put("kind", "DaemonSet");
                      }
                    }));
          }
        });

    var testDeployManifestStage5 = new StageExecutionImpl(testPipeline, "runJobManifest", context);
    testDeployManifestStage5.setName("Deploy");
    testDeployManifestStage5.setStatus(ExecutionStatus.SUCCEEDED);
    testDeployManifestStage5.setOutputs(
        new HashMap<>() {
          {
            put(
                "manifests",
                List.of(
                    new HashMap<>() {
                      {
                        put(
                            "metadata",
                            new HashMap<>() {
                              {
                                put("name", "unknown");
                              }
                            });
                        put("kind", "Deployment");
                      }
                    }));
          }
        });

    var testDeployManifestStage6 = new StageExecutionImpl(testPipeline, "runJobManifest", context);
    testDeployManifestStage6.setName("Deploy");
    testDeployManifestStage6.setStatus(ExecutionStatus.SUCCEEDED);
    testDeployManifestStage6.setOutputs(
        new HashMap<>() {
          {
            put(
                "manifests",
                List.of(
                    new HashMap<>() {
                      {
                        put(
                            "metadata",
                            new HashMap<>() {
                              {
                                put("name", "unknown");
                              }
                            });
                        put("kind", "StatefulSet");
                      }
                    }));
          }
        });

    var testDeployManifestStage7 = new StageExecutionImpl(testPipeline, "runJobManifest", context);
    testDeployManifestStage7.setName("Deploy");
    testDeployManifestStage7.setStatus(ExecutionStatus.SUCCEEDED);
    testDeployManifestStage7.setOutputs(
        new HashMap<>() {
          {
            put("manifests", List.of());
          }
        });

    var testDeployManifestStage8 = new StageExecutionImpl(testPipeline, "deployManifest", context);
    testDeployManifestStage8.setName("Deploy");
    testDeployManifestStage8.setStatus(ExecutionStatus.SUCCEEDED);
    testDeployManifestStage8.setOutputs(
        new HashMap<>() {
          {
            put("manifests", List.of(new HashMap<>()));
          }
        });

    var testDeployManifestStage9 = new StageExecutionImpl(testPipeline, "runJobManifest", context);
    testDeployManifestStage9.setName("Deploy");
    testDeployManifestStage9.setStatus(ExecutionStatus.SUCCEEDED);
    testDeployManifestStage9.setOutputs(
        new HashMap<>() {
          {
            put(
                "manifests",
                List.of(
                    new HashMap<>() {
                      {
                        put(
                            "metadata",
                            new HashMap<>() {
                              {
                                put("name", "test_app");
                              }
                            });
                        put("kind", "Unknown");
                      }
                    }));
          }
        });

    var testDeployManifestStage10 = new StageExecutionImpl(testPipeline, "runJobManifest", context);
    testDeployManifestStage10.setName("Deploy");
    testDeployManifestStage10.setStatus(ExecutionStatus.SUCCEEDED);

    testPipeline.getStages().add(testDeployManifestStage1);
    testPipeline.getStages().add(testDeployManifestStage2);
    testPipeline.getStages().add(testDeployManifestStage3);
    testPipeline.getStages().add(testDeployManifestStage4);
    testPipeline.getStages().add(testDeployManifestStage5);
    testPipeline.getStages().add(testDeployManifestStage6);
    testPipeline.getStages().add(testDeployManifestStage7);
    testPipeline.getStages().add(testDeployManifestStage8);
    testPipeline.getStages().add(testDeployManifestStage9);
    testPipeline.getStages().add(testDeployManifestStage10);

    var result = task.execute(stageExecution);

    assertEquals(ExecutionStatus.SUCCEEDED, result.getStatus());
  }

  @Test
  public void shouldFinishWithRedirectWhenAllChildPipelinesSucceeded() {
    var testPipelineId1 = "test_child_1";
    var testPipelineId2 = "test_child_2";

    var task = new MonitorMultiplePipelinesTask(executionRepository, objectMapper);

    var context = new HashMap<String, Object>();
    context.put("levelNumber", 0);
    context.put(
        "orderOfExecutions",
        List.of(
            List.of(
                new HashMap<String, Object>() {
                  {
                    put("yamlIdentifier", testPipelineId1);
                    put("arguments", new HashMap<>());
                    put("child_pipeline", testPipelineId1);
                  }
                },
                new HashMap<String, Object>() {
                  {
                    put("yamlIdentifier", testPipelineId2);
                    put("arguments", new HashMap<>());
                    put("child_pipeline", testPipelineId2);
                  }
                })));
    context.put("orderOfExecutionsSize", 2);
    context.put("executionIds", List.of(testPipelineId1, testPipelineId2));

    var stageExecution = new StageExecutionImpl(pipeline, "runMultiplePipelines", context);

    pipeline.getStages().add(stageExecution);

    var testParentPipeline = new PipelineExecutionImpl(PIPELINE, testApplication);
    testParentPipeline.setStatus(ExecutionStatus.RUNNING);

    var testPipeline1 = new PipelineExecutionImpl(PIPELINE, testApplication);
    testPipeline1.setStatus(ExecutionStatus.SUCCEEDED);
    testPipeline1.setTrigger(new PipelineTrigger(testParentPipeline));

    var testPipeline2 = new PipelineExecutionImpl(PIPELINE, testApplication);
    testPipeline2.setStatus(ExecutionStatus.SUCCEEDED);
    testPipeline2.setTrigger(new PipelineTrigger(testParentPipeline));

    when(executionRepository.retrieve(PIPELINE, testPipelineId1)).thenReturn(testPipeline1);
    when(executionRepository.retrieve(PIPELINE, testPipelineId2)).thenReturn(testPipeline2);

    var result = task.execute(stageExecution);

    assertEquals(ExecutionStatus.REDIRECT, result.getStatus());
  }

  @Test
  public void shouldFinishWithRedirectWhenAnyChildPipelinesFailed() {
    var testPipelineId1 = "test_child_1";
    var testPipelineId2 = "test_child_2";

    var task = new MonitorMultiplePipelinesTask(executionRepository, objectMapper);

    var context = new HashMap<String, Object>();
    context.put("levelNumber", 0);
    context.put(
        "orderOfExecutions",
        List.of(
            List.of(
                new HashMap<String, Object>() {
                  {
                    put("yamlIdentifier", testPipelineId1);
                    put("arguments", new HashMap<>());
                    put("child_pipeline", testPipelineId1);
                  }
                },
                new HashMap<String, Object>() {
                  {
                    put("yamlIdentifier", testPipelineId2);
                    put("arguments", new HashMap<>());
                    put("child_pipeline", testPipelineId2);
                  }
                })));
    context.put("orderOfExecutionsSize", 2);
    context.put("executionIds", List.of(testPipelineId1, testPipelineId2));

    var stageExecution = new StageExecutionImpl(pipeline, "runMultiplePipelines", context);
    stageExecution.getContext().put("ignoreUncompleted", true);

    pipeline.getStages().add(stageExecution);

    var testParentPipeline = new PipelineExecutionImpl(PIPELINE, testApplication);
    testParentPipeline.setStatus(ExecutionStatus.RUNNING);

    var testPipeline1 = new PipelineExecutionImpl(PIPELINE, testApplication);
    testPipeline1.setStatus(ExecutionStatus.SUCCEEDED);
    testPipeline1.setTrigger(new PipelineTrigger(testParentPipeline));

    var testPipeline2 = new PipelineExecutionImpl(PIPELINE, testApplication);
    testPipeline2.setStatus(ExecutionStatus.TERMINAL);
    testPipeline2.setTrigger(new PipelineTrigger(testParentPipeline));

    when(executionRepository.retrieve(PIPELINE, testPipelineId1)).thenReturn(testPipeline1);
    when(executionRepository.retrieve(PIPELINE, testPipelineId2)).thenReturn(testPipeline2);

    var result = task.execute(stageExecution);

    assertEquals(ExecutionStatus.REDIRECT, result.getStatus());
  }

  @Test
  public void shouldFinishWithRedirectWhenAllChildPipelinesCompleted() {
    var testPipelineId1 = "test_child_1";
    var testPipelineId2 = "test_child_2";

    var task = new MonitorMultiplePipelinesTask(executionRepository, objectMapper);

    var context = new HashMap<String, Object>();
    context.put("levelNumber", 0);
    context.put(
        "orderOfExecutions",
        List.of(
            List.of(
                new HashMap<String, Object>() {
                  {
                    put("yamlIdentifier", testPipelineId1);
                    put("arguments", new HashMap<>());
                    put("child_pipeline", testPipelineId1);
                  }
                },
                new HashMap<String, Object>() {
                  {
                    put("yamlIdentifier", testPipelineId2);
                    put("arguments", new HashMap<>());
                    put("child_pipeline", testPipelineId2);
                  }
                })));
    context.put("orderOfExecutionsSize", 2);
    context.put("executionIds", List.of(testPipelineId1, testPipelineId2));

    var stageExecution = new StageExecutionImpl(pipeline, "runMultiplePipelines", context);
    stageExecution.getContext().put("ignoreUncompleted", true);

    pipeline.getStages().add(stageExecution);

    var testParentPipeline = new PipelineExecutionImpl(PIPELINE, testApplication);
    testParentPipeline.setStatus(ExecutionStatus.RUNNING);

    var testPipeline1 = new PipelineExecutionImpl(PIPELINE, testApplication);
    testPipeline1.setStatus(ExecutionStatus.SUCCEEDED);
    testPipeline1.setTrigger(new PipelineTrigger(testParentPipeline));

    var testPipeline2 = new PipelineExecutionImpl(PIPELINE, testApplication);
    testPipeline2.setStatus(ExecutionStatus.CANCELED);
    testPipeline2.setTrigger(new PipelineTrigger(testParentPipeline));

    when(executionRepository.retrieve(PIPELINE, testPipelineId1)).thenReturn(testPipeline1);
    when(executionRepository.retrieve(PIPELINE, testPipelineId2)).thenReturn(testPipeline2);

    var result = task.execute(stageExecution);

    assertEquals(ExecutionStatus.REDIRECT, result.getStatus());
  }

  @Test
  public void shouldMonitorMultiplePipelinesTaskFinishSuccessfullyWithTerminal() {
    var testPipelineId1 = "test_child_1";
    var testPipelineId2 = "test_child_2";

    var task = new MonitorMultiplePipelinesTask(executionRepository, objectMapper);

    var context = new HashMap<String, Object>();
    context.put("levelNumber", 0);
    context.put(
        "orderOfExecutions",
        List.of(
            List.of(
                new HashMap<String, Object>() {
                  {
                    put("yamlIdentifier", testPipelineId1);
                    put("arguments", new HashMap<>());
                    put("child_pipeline", testPipelineId1);
                  }
                },
                new HashMap<String, Object>() {
                  {
                    put("yamlIdentifier", testPipelineId2);
                    put("arguments", new HashMap<>());
                    put("child_pipeline", testPipelineId2);
                  }
                })));
    context.put("orderOfExecutionsSize", 2);
    context.put("executionIds", List.of(testPipelineId1, testPipelineId2));

    var stageExecution = new StageExecutionImpl(pipeline, "runMultiplePipelines", context);

    pipeline.getStages().add(stageExecution);

    var testParentPipeline = new PipelineExecutionImpl(PIPELINE, testApplication);
    testParentPipeline.setName("testParentPipeline");
    testParentPipeline.setStatus(ExecutionStatus.RUNNING);

    var testPipeline1 = new PipelineExecutionImpl(PIPELINE, testApplication);
    testPipeline1.setName("testPipeline1");
    testPipeline1.setStatus(ExecutionStatus.SUCCEEDED);
    testPipeline1.setTrigger(new PipelineTrigger(testParentPipeline));

    var testPipeline2 = new PipelineExecutionImpl(PIPELINE, testApplication);
    testPipeline2.setName("testPipeline2");
    testPipeline2.setStatus(ExecutionStatus.TERMINAL);
    testPipeline2.setTrigger(new PipelineTrigger(testParentPipeline));

    var testStageWithTerminalStatus1 =
        new StageExecutionImpl(testPipeline2, "testStageWithTerminalStatus1");
    testStageWithTerminalStatus1.setStatus(ExecutionStatus.TERMINAL);
    testStageWithTerminalStatus1.setContext(
        new HashMap<>() {
          {
            put(
                "exception",
                new HashMap<>() {
                  {
                    put(
                        "details",
                        new HashMap<>() {
                          {
                            put("error", "Something went wrong 1.");
                          }
                        });
                  }
                });
          }
        });

    var testStageWithTerminalStatus2 =
        new StageExecutionImpl(testPipeline2, "testStageWithTerminalStatus2");
    testStageWithTerminalStatus2.setStatus(ExecutionStatus.TERMINAL);
    testStageWithTerminalStatus2.setContext(
        new HashMap<>() {
          {
            put(
                "exception",
                new HashMap<>() {
                  {
                    put(
                        "details",
                        new HashMap<>() {
                          {
                            put("errors", "Something went wrong 2.");
                          }
                        });
                  }
                });
          }
        });

    var testStageWithTerminalStatus3 =
        new StageExecutionImpl(testPipeline2, "testStageWithTerminalStatus3");
    testStageWithTerminalStatus3.setStatus(ExecutionStatus.TERMINAL);
    testStageWithTerminalStatus3.setContext(
        new HashMap<>() {
          {
            put(
                "kato.tasks",
                List.of(
                    new HashMap<>() {
                      {
                        put(
                            "status",
                            new HashMap<>() {
                              {
                                put("failed", true);
                              }
                            });
                        put(
                            "exception",
                            new HashMap<>() {
                              {
                                put("message", "Something went wrong 3.");
                              }
                            });
                      }
                    },
                    new HashMap<>() {
                      {
                        put(
                            "status",
                            new HashMap<>() {
                              {
                                put("failed", true);
                              }
                            });
                        put("history", List.of("Something went wrong 4."));
                      }
                    },
                    new HashMap<>() {
                      {
                        put(
                            "status",
                            new HashMap<>() {
                              {
                                put("failed", true);
                              }
                            });
                      }
                    }));
          }
        });

    var testStageWithTerminalStatus4 =
        new StageExecutionImpl(testPipeline2, "testStageWithTerminalStatus4");
    testStageWithTerminalStatus4.setStatus(ExecutionStatus.TERMINAL);
    testStageWithTerminalStatus4.setContext(new HashMap<>());

    testPipeline2.getStages().add(testStageWithTerminalStatus1);
    testPipeline2.getStages().add(testStageWithTerminalStatus2);
    testPipeline2.getStages().add(testStageWithTerminalStatus3);
    testPipeline2.getStages().add(testStageWithTerminalStatus4);

    when(executionRepository.retrieve(PIPELINE, testPipelineId1)).thenReturn(testPipeline1);
    when(executionRepository.retrieve(PIPELINE, testPipelineId2)).thenReturn(testPipeline2);

    var result = task.execute(stageExecution);

    assertEquals(ExecutionStatus.TERMINAL, result.getStatus());
  }

  @Test
  public void shouldMonitorMultiplePipelinesTaskFinishSuccessfullyWithTerminalAndLegacyStage() {
    var testPipelineId = "test_child";

    var task = new MonitorMultiplePipelinesTask(executionRepository, objectMapper);

    var context = new HashMap<String, Object>();
    context.put("levelNumber", 0);
    context.put(
        "orderOfExecutions",
        List.of(
            List.of(
                new HashMap<String, Object>() {
                  {
                    put("yamlIdentifier", testPipelineId);
                    put("arguments", new HashMap<>());
                    put("child_pipeline", testPipelineId);
                  }
                })));
    context.put("orderOfExecutionsSize", 1);
    context.put("executionId", testPipelineId);

    var stageExecution = new StageExecutionImpl(pipeline, "runJobManifest", context);

    pipeline.getStages().add(stageExecution);

    var testParentPipeline = new PipelineExecutionImpl(PIPELINE, testApplication);
    testParentPipeline.setName("testParentPipeline");
    testParentPipeline.setStatus(ExecutionStatus.RUNNING);

    var testPipeline = new PipelineExecutionImpl(PIPELINE, testApplication);
    testPipeline.setName("testPipeline");
    testPipeline.setStatus(ExecutionStatus.TERMINAL);
    testPipeline.setTrigger(new PipelineTrigger(testParentPipeline));

    when(executionRepository.retrieve(PIPELINE, testPipelineId)).thenReturn(testPipeline);

    var result = task.execute(stageExecution);

    assertEquals(ExecutionStatus.TERMINAL, result.getStatus());
  }

  @Test
  public void shouldMonitorMultiplePipelinesTaskFinishSuccessfullyWithCanceled() {
    var testPipelineId1 = "test_child_1";
    var testPipelineId2 = "test_child_2";

    var task = new MonitorMultiplePipelinesTask(executionRepository, objectMapper);

    var context = new HashMap<String, Object>();
    context.put("levelNumber", 0);
    context.put(
        "orderOfExecutions",
        List.of(
            List.of(
                new HashMap<String, Object>() {
                  {
                    put("yamlIdentifier", testPipelineId1);
                    put("arguments", new HashMap<>());
                    put("child_pipeline", testPipelineId1);
                  }
                },
                new HashMap<String, Object>() {
                  {
                    put("yamlIdentifier", testPipelineId2);
                    put("arguments", new HashMap<>());
                    put("child_pipeline", testPipelineId2);
                  }
                })));
    context.put("orderOfExecutionsSize", 2);
    context.put("executionIds", List.of(testPipelineId1, testPipelineId2));

    var stageExecution = new StageExecutionImpl(pipeline, "runMultiplePipelines", context);

    pipeline.getStages().add(stageExecution);

    var testParentPipeline = new PipelineExecutionImpl(PIPELINE, testApplication);
    testParentPipeline.setStatus(ExecutionStatus.RUNNING);

    var testPipeline1 = new PipelineExecutionImpl(PIPELINE, testApplication);
    testPipeline1.setStatus(ExecutionStatus.SUCCEEDED);
    testPipeline1.setTrigger(new PipelineTrigger(testParentPipeline));

    var testPipeline2 = new PipelineExecutionImpl(PIPELINE, testApplication);
    testPipeline2.setStatus(ExecutionStatus.CANCELED);
    testPipeline2.setTrigger(new PipelineTrigger(testParentPipeline));

    when(executionRepository.retrieve(PIPELINE, testPipelineId1)).thenReturn(testPipeline1);
    when(executionRepository.retrieve(PIPELINE, testPipelineId2)).thenReturn(testPipeline2);

    var result = task.execute(stageExecution);

    assertEquals(ExecutionStatus.CANCELED, result.getStatus());
  }

  @Test
  public void shouldMonitorMultiplePipelinesTaskFinishSuccessfullyWithRunning() {
    var testPipelineId1 = "test_child_1";
    var testPipelineId2 = "test_child_2";

    var task = new MonitorMultiplePipelinesTask(executionRepository, objectMapper);

    var context = new HashMap<String, Object>();
    context.put("levelNumber", 0);
    context.put(
        "orderOfExecutions",
        List.of(
            List.of(
                new HashMap<String, Object>() {
                  {
                    put("yamlIdentifier", testPipelineId1);
                    put("arguments", new HashMap<>());
                    put("child_pipeline", testPipelineId1);
                  }
                },
                new HashMap<String, Object>() {
                  {
                    put("yamlIdentifier", testPipelineId2);
                    put("arguments", new HashMap<>());
                    put("child_pipeline", testPipelineId2);
                  }
                })));
    context.put("orderOfExecutionsSize", 2);
    context.put("executionIds", List.of(testPipelineId1, testPipelineId2));

    var stageExecution = new StageExecutionImpl(pipeline, "runMultiplePipelines", context);

    pipeline.getStages().add(stageExecution);

    var testParentPipeline = new PipelineExecutionImpl(PIPELINE, testApplication);
    testParentPipeline.setStatus(ExecutionStatus.RUNNING);

    var testPipeline1 = new PipelineExecutionImpl(PIPELINE, testApplication);
    testPipeline1.setStatus(ExecutionStatus.SUCCEEDED);
    testPipeline1.setTrigger(new PipelineTrigger(testParentPipeline));

    var testPipeline2 = new PipelineExecutionImpl(PIPELINE, testApplication);
    testPipeline2.setStatus(ExecutionStatus.RUNNING);
    testPipeline2.setTrigger(new PipelineTrigger(testParentPipeline));

    when(executionRepository.retrieve(PIPELINE, testPipelineId1)).thenReturn(testPipeline1);
    when(executionRepository.retrieve(PIPELINE, testPipelineId2)).thenReturn(testPipeline2);

    var result = task.execute(stageExecution);

    assertEquals(ExecutionStatus.RUNNING, result.getStatus());
  }
}
