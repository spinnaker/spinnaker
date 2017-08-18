<#if (notification.additionalContext.execution.name)??>
  <#if (notification.additionalContext.execution.trigger.buildInfo.number)??>
Stage ${notification.additionalContext.stageName} for ${notification.source.application}'s ${notification.additionalContext.execution.name} pipeline build ${notification.additionalContext.execution.trigger.buildInfo.number} was judged to continue by ${notification.additionalContext.judgedBy}
  <#else>
Stage ${notification.additionalContext.stageName} for ${notification.source.application}'s ${notification.additionalContext.execution.name} pipeline was judged to continue by ${notification.additionalContext.judgedBy}
  </#if>
<#else>
Stage ${notification.additionalContext.stageName} for ${notification.source.application} was judged to continue by ${notification.additionalContext.judgedBy}
</#if>
