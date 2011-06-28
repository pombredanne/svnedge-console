<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
        <meta name="layout" content="main" />
        <title>CollabNet Subversion Edge <g:message code=repository.page.list.header.title /></title>
        <g:render template="/common/listViewResources"/>

    </head>

<g:render template="leftNav" />

<content tag="title">
   <g:message code="repository.page.leftnav.title" />
</content>
    
    <body>

    <g:set var="colCount">
      <g:ifAnyGranted role="ROLE_ADMIN,ROLE_ADMIN_REPO">5</g:ifAnyGranted>
      <g:ifNotGranted role="ROLE_ADMIN,ROLE_ADMIN_REPO">3</g:ifNotGranted>
    </g:set>

    <g:form>
    <table class="Container">
    <tbody>

    <g:if test="${repositoryInstanceList.size() > 0}">
        <tr class="ContainerHeader">
          <td colspan="${colCount}"><g:message code="repository.page.list.header" /></td>
        </tr>
        <tr class="ItemListHeader">
          <g:ifAnyGranted role="ROLE_ADMIN,ROLE_ADMIN_REPO">
            <th><g:listViewSelectAll/></th>
          </g:ifAnyGranted>
          <g:sortableColumn property="name" title="${message(code:'repository.page.list.name')}" />
          <th><g:message code="repository.page.list.checkout_command" /></th>
          <g:sortableColumn property="permissionsOk" title="${message(code:'repository.page.list.status')}" />
          <g:ifAnyGranted role="ROLE_ADMIN,ROLE_ADMIN_REPO">
            <th><g:message code="repository.page.list.show" /></th>
          </g:ifAnyGranted>

         </tr>
         <g:each in="${repositoryInstanceList}" status="i" var="repositoryInstance">
         <g:set var="repoName" value="${repositoryInstance.name}"/>
           <tr class="${(i % 2) == 0 ? 'EvenRow' : 'OddRow'}">
             <g:ifAnyGranted role="ROLE_ADMIN,ROLE_ADMIN_REPO">
             <td><g:listViewSelectItem item="${repositoryInstance}"/></td>
           </g:ifAnyGranted>
                            <g:set var="viewvcURL" value="${server.viewvcURL(repoName)}"/>
                            <g:if test="${viewvcURL}">
                              <td><a href="${viewvcURL}" target="_blank">${repoName}</a></td>
                            </g:if>
                            <g:else>
                              <td>${repoName}</td>
                            </g:else>


                <td>svn co ${server.svnURL()}${repoName} ${repoName} --username=<g:loggedInUsername/></td>
                  <td>
                    <g:if test="${repositoryInstance.permissionsOk}">
                      <span style="color:green"><g:message code="repository.page.list.instance.permission.ok" /></span>
                    </g:if>
                    <g:else>
                      <span style="color:red"><g:message code="repository.page.list.instance.permission.needFix" /></span>
                    </g:else>

                  </td>

      <g:ifAnyGranted role="ROLE_ADMIN,ROLE_ADMIN_REPO">
                            <td><g:link action="show" id="${repositoryInstance.id}"><g:message code="repository.page.list.instance.info" /></g:link></td>
      </g:ifAnyGranted>
                        </tr>
                    </g:each>
                    <tr class="ContainerFooter">
                       <td colspan="${colCount}">
                         <div class="AlignLeft">
                                  <div class="paginateButtons">
                <g:paginate total="${repositoryInstanceTotal}" />
            </div>
                         </div>

                         <div class="AlignRight">
                             <g:listViewActionButton action="deleteMultiple" minSelected="1" maxSelected="1"
                                 confirmMessage="${message(code:'repository.page.list.delete.confirmation')}"
                                 confirmTypeThis="${message(code:'default.confirmation.typeThis')}">
                               <g:message code="default.button.delete.label"/>
                             </g:listViewActionButton>
                         </div>

                       </td>
                    </tr>
            </div>
        </div>
    </g:if>

    <g:else>
      <g:if test="${isReplica}">
        <p><g:message code="repository.page.list.replica.noRepos" /></p>
      </g:if>
      <g:else>
        <p><g:message code="repository.page.list.noRepos" /></p>
      </g:else>
    </g:else>

       </tbody>
      </table>
      </g:form>
    </body>
</html>
