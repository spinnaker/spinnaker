<#if (notification.additionalContext.execution.name)??>
  <#if (notification.additionalContext.execution.trigger.buildInfo.number)??>
Stage ${notification.additionalContext.stageName} for ${notification.source.application}'s ${notification.additionalContext.execution.name} pipeline build ${notification.additionalContext.execution.trigger.buildInfo.number?c} is awaiting manual judgment.
  <#else>
Stage ${notification.additionalContext.stageName} for ${notification.source.application}'s ${notification.additionalContext.execution.name} pipeline is awaiting manual judgment.
  </#if>
<#else>
Stage ${notification.additionalContext.stageName} for ${notification.source.application} is awaiting manual judgment.
</#if>

<#if (notification.additionalContext.instructions)??>
*Instructions:*
${htmlToText.convert(notification.additionalContext.instructions)}

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
