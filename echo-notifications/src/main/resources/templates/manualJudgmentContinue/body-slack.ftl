<#include "../manualJudgment/variables-slack.ftl">
${stageDescription} _was judged to *continue*_ by ${notification.additionalContext.judgedBy}.

<#if (notification.additionalContext.message)??>
${htmlToText.convert(notification.additionalContext.message)}
</#if>

<#if (notification.additionalContext.judgmentInput)??>
Judgment '*${htmlToText.convert(notification.additionalContext.judgmentInput)}*' was selected.
</#if>
