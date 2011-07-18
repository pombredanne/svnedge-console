%{--
  - CollabNet Subversion Edge
  - Copyright (C) 2011, CollabNet Inc. All rights reserved.
  -
  - This program is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Affero General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - This program is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Affero General Public License for more details.
  -
  - You should have received a copy of the GNU Affero General Public License
  - along with this program.  If not, see <http://www.gnu.org/licenses/>.
  --}%

<%@page import="com.collabnet.svnedge.integration.command.ShortRunningCommand"%>
<%@page import="com.collabnet.svnedge.integration.command.AbstractCommand"%>
<%@page import="com.collabnet.svnedge.integration.command.CommandState"%>

<table class="Container">
  <thead>
    <tr class="ContainerHeader">
      <td colspan="3">
       <g:if test="${shortRun}">
        <g:message code="job.page.list.short_running.header"/>
       </g:if>
       <g:else>
        <g:message code="job.page.list.long_running.header"/>
       </g:else>
      </td>
      <td>
        <g:set var="imageRunning" value="none" />
        <g:if test="${runningCommands.size() > 0}">
           <g:set var="imageRunning" value="" />
        </g:if>
      </td>
    </tr>
     <tr class="ItemListHeader">
       <td width="18">#</td>
       <td width="15%">${message(code: 'job.page.list.column.id')}</td>
       <td>${message(code: 'job.page.list.column.code')}</td>
       <td width="20%">${message(code: 'job.page.list.column.started_at')}</td>
    </tr>
  </thead>
  <tbody id="${tableName}">
   <g:each in="${(0..maxNumber-1)}" var="i">
    <g:if test="${i < runningCommands.size()}">
     <g:set var="command" value="${runningCommands.get(i)}" />

    <g:if test="${command.state == CommandState.RUNNING}">
      <tr id="run_${command.id}" class="${(i % 2) == 0 ? 'OddRow' : 'EvenRow'}">
    </g:if>
    <g:elseif test="${(command.state == CommandState.TERMINATED || command.state == CommandState.REPORTED) && command.succeeded}">
      <tr id="run_${command.id}" class="${(i % 2) == 0 ? 'OddRow' : 'EvenRow'}" style="background-color : #99D6AD;">
    </g:elseif>
    <g:elseif test="${(command.state == CommandState.TERMINATED || command.state == CommandState.REPORTED) && !command.succeeded}">
      <tr id="run_${command.id}" class="${(i % 2) == 0 ? 'OddRow' : 'EvenRow'}" style="background-color : #FFB2B2;">
    </g:elseif>

       <td>${i+1}</td>
       <g:set var="commandCode" value="${AbstractCommand.makeCodeName(command)}" />
       <g:set var="commandDesc" value="job.page.list.${commandCode}" />
         <g:if test="${command?.params?.repoName}">
           <g:set var="repoName" value="${command.params.repoName.substring(command.params.repoName.lastIndexOf("/") + 1)}" />
         </g:if>
       <td>
         <g:if test="${commandCode.contains('replica') || command.state == CommandState.REPORTED}">
            ${command.id}
         </g:if>
         <g:elseif test="${command.state == CommandState.RUNNING || command.state == CommandState.TERMINATED}">
            <a target="${command.id}" href="/csvn/log/show?fileName=/temp/${command.id}.log&view=tail">${command.id}</a>
         </g:elseif>
       </td>

       <td>
         <img border="0" src="/csvn/images/replica/${commandCode}.png"> 
         <g:if test="${command?.params?.repoName}">
           <g:set var="repoName" value="${command.params.repoName.substring(command.params.repoName.lastIndexOf('/') + 1, command.params.repoName.length())}" />
           ${message(code: commandDesc, args:[repoName])}
         </g:if>
         <g:else>
           ${message(code: commandDesc)}
         </g:else>
         <g:if test="${!command?.params?.repoName}">
           ${command.params}
         </g:if>
       </td>
       <td>
        <g:formatDate format="${logDateFormat}"
             date="${new Date(command.getCurrentStateTransitionTime())}"/>
       </td>
      </tr> 
    </g:if>
    <g:else>
      <tr class="${(i % 2) == 0 ? 'OddRow' : 'EvenRow'}">
       <td>${i+1}</td>
       <td colspan="3" align="center"><b>${message(code: 'job.page.list.row.job_idle')}</b></td>
      </tr>
    </g:else>
     </tr>
   </g:each>
  </tbody>
 </table>