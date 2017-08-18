<#if (notification.additionalContext.execution.name)??>
  <#if (notification.additionalContext.execution.trigger.buildInfo.number)??>
Stage ${notification.additionalContext.stageName} for ${notification.source.application}'s ${notification.additionalContext.execution.name} pipeline build ${notification.additionalContext.execution.trigger.buildInfo.number} was judged to stop.
  <#else>
Stage ${notification.additionalContext.stageName} for ${notification.source.application}'s ${notification.additionalContext.execution.name} pipeline was judged to stop.
  </#if>
<#else>
Stage ${notification.additionalContext.stageName} for ${notification.source.application} was judged to stop.
</#if>
<#if (notification.additionalContext.message)??>
${htmlToText.convert(notification.additionalContext.message)}
</#if>

For more details, please visit:
<#if (notification.additionalContext.stageId)??>
  <#if (notification.additionalContext.restrictExecutionDuringTimeWindow)??>
${baseUrl}/#/applications/${notification.source.application}/executions/details/${notification.source.executionId}?refId=${notification.additionalContext.stageId}&step=1
  <#else>
${baseUrl}/#/applications/${notification.source.application}/executions/details/${notification.source.executionId}?refId=${notification.additionalContext.stageId}
  </#if>
<#else>
${baseUrl}/#/applications/${notification.source.application}/executions/details/${notification.source.executionId}
</#if>
