/*
 * CollabNet Subversion Edge
 * Copyright (C) 2010, CollabNet Inc. All rights reserved.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.collabnet.svnedge.console.services

import java.net.URL;
import java.net.URLEncoder;
import java.net.HttpURLConnection;
import java.net.UnknownHostException
import java.net.NoRouteToHostException
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import grails.util.GrailsUtil

import com.collabnet.ce.soap50.webservices.ClientSoapStubFactory
import com.collabnet.ce.soap50.webservices.cemain.ICollabNetSoap
import com.collabnet.ce.soap50.webservices.scm.IScmAppSoap
import com.collabnet.ce.soap50.webservices.cemain.ProjectSoapDO
import com.collabnet.ce.soap50.webservices.cemain.UserSoapDO
import com.collabnet.svnedge.master.ctf.CtfAuthenticationException
import com.collabnet.svnedge.master.RemoteAndLocalConversationException
import com.collabnet.svnedge.master.RemoteMasterException
import com.collabnet.svnedge.teamforge.CtfConversionBean
import com.collabnet.svnedge.console.Repository
import com.collabnet.svnedge.console.Server
import com.collabnet.svnedge.console.ServerMode
import com.collabnet.svnedge.console.security.User
import com.collabnet.svnedge.console.CantBindPortException
import com.collabnet.svnedge.teamforge.CtfServer

class SetupTeamForgeService {

    static final int CTF_REPO_PATH_LIMIT = 128

    def lifecycleService
    def serverConfService
    def commandLineService
    def operatingSystemService
    def authenticationManager
    def ctfAuthenticationProvider
    def ctfRemoteClientService
    def securityService
    def svnRepoService
    def daoAuthenticationProvider
    def anonymousAuthenticationProvider

    /**
     * The integration properties file.
     */
    private static def TF_PROPERTIES_FILE = "data/conf/teamforge.properties"
    /**
     * The team forge integration path.
     */
    private static def TF_PATH_VAR = "sf.sourceForgePropertiesPath"

    /**
     * Path to parent directory of viewvc.cgi
     */
    private static final String CGI_PATH_VAR = "viewvc.cgi.path"

    /**
     * Path to application home, used by csvn's viewvc.cgi
     */
    private static final String APP_HOME_VAR = "csvn.appHome"

    boolean transactional = true

    /**
    * Sets system properties used to configure the integration webapp
    * and scripts
    *
    * @param appHome is the application home directory
    */
   def bootStrap = { appHome ->
       def tfPropertiesFile = new File(appHome, TF_PROPERTIES_FILE);
       System.properties[TF_PATH_VAR] = tfPropertiesFile.absolutePath
       log.debug("Updating system property '${TF_PATH_VAR}'='" +
                 "${System.properties[TF_PATH_VAR]}'")
       System.properties[CGI_PATH_VAR] =
           new File(appHome, "bin/cgi-bin").absolutePath
       log.debug("Updating system property '${CGI_PATH_VAR}'='" +
                 "${System.properties[CGI_PATH_VAR]}'")
       System.properties[APP_HOME_VAR] = appHome
       log.debug("Setting system property '${APP_HOME_VAR}'='" +
                 "${System.properties[APP_HOME_VAR]}'")

        updateIntegrationScripts(appHome)
        updateTeamForgeProperties(appHome)
   }
   
   private void updateIntegrationScripts(appHome) {
       def server = Server.getServer()
       // In ctf mode, confirm that the integration scripts are up-to-date
       if (server && server.mode == ServerMode.MANAGED) {
           File libDir = new File(appHome, "lib")
           File zipFile = new File(libDir, INTEGRATION_SCRIPTS_ZIP)
           File integrationDir = new File(libDir, "integration")
           if (zipFile.exists() && (!integrationDir.exists() ||
               zipFile.lastModified() > integrationDir.lastModified())) {
               log.info("Updating CTF integration scripts.")
               unpackIntegrationScripts()
           }
       }
   }

   private void updateTeamForgeProperties(appHome) {
       File confDir = new File(appHome, "data/conf")
       File tfProps = new File(confDir, "teamforge.properties.dist")
       if (tfProps.exists()) {
           String text = tfProps.text
           if (text.indexOf("sfmain.integration.executables.python") < 0) {
               int pos = text.indexOf("# Integration listener keys")
               if (pos > 0) {
                   text = text.substring(0, pos) + 
                       "\nsfmain.integration.executables.python=python\n" +
                       text.substring(pos)
               } else {
                   text += "\nsfmain.integration.executables.python=python\n"
               }
               tfProps.write(text)
               serverConfService.writeConfigFiles()
           }
       }
   }
   
   private String getWebSessionId(conversionData) {
        if (!conversionData.webSessionId) {
            def ids = loginCtfWebapp(conversionData)
            conversionData.webSessionId = ids.jsessionid
            conversionData.userSessionId = ids.usessionid
        }
        conversionData.webSessionId
    }
    
    private String getUserSessionId(conversionData) {
        if (!conversionData.userSessionId) {
            def ids = loginCtfWebapp(conversionData)
            conversionData.webSessionId = ids.jsessionid
            conversionData.userSessionId = ids.usessionid
        }
        conversionData.userSessionId
    }

    /**
     * @param conversionData contains the conversion data from wizard.
     * @param projectName is the name of the project to be verified.
     * @return if the given project name exists in the TeamForge server given
     * in the conversionData.
     * @throws CtfSessionExpiredException in case the session expires.
     */
    def String projectExists(conversionData, projectName) 
        throws RemoteMasterException {

        return ctfRemoteClientService.projectExists(conversionData.ctfURL,
            conversionData.userSessionId, projectName, projectUrl(projectName))
    }

    /**
     * @param conversionData is the wizard bean.
     * @return The list of remote TeamForge projects that are named with
     * the same name as the local repositories on CSVN.
     * @throws RemoteMasterException in case the call to the remote TeamForge
     * SOAP API to return the list of projects fail.
     */
    def List<String> getProjectsWhichMatchRepoNames(conversionData) 
        throws RemoteMasterException {

        def ctfProjectsList = ctfRemoteClientService.getProjectList(
            conversionData.ctfURL, conversionData.userSessionId)
        def projects = new HashSet(ctfProjectsList.dataRows.toList().collect {
            it.title })
        def repos = Repository.list(sort:"name")
        List<String> repoNames = new ArrayList<String>(repos.size())
        for (repo in repos) {
            if (projects.contains(repo.name)) {
                repoNames << repo.name
            }
        }
        repoNames
    }

    private static final def FIXABLE_REPO_NAME = ~/[_A-Za-z0-9]*/
    private static final def UPPER_CHAR = ~/[A-Z]/

    /**
     * CTF only allows repo directories which start with a letter and use
     * letters, numbers, and underscore in the name.  This method sets
     * several flags or lists in the wizard bean related to invalid repos
     */
    def validateRepoNames() {
        def repoParent = Server.getServer().repoParentDir
        def repos = Repository.list(sort:'name')
        def repoNames = new HashSet(repos.collect({ it.name }))
        def unfixableRepoNames = []
        def duplicatedReposIgnoringCase = []
        def containsUpperCaseRepos = false
        def containsReposWithInvalidFirstChar = false
        def longRepoPath = null
        for (repo in repos) {
            def repoName = repo.name
            if (!repo.validateName()) {
                if (repoName.matches(FIXABLE_REPO_NAME)) {
                    if (repoName.find(UPPER_CHAR)) {
                        containsUpperCaseRepos = true
                        if (repoNames.contains(repoName.toLowerCase())) {
                            duplicatedReposIgnoringCase << repoName
                        }
                    }     
                    char firstChar = repoName.charAt(0)
                    if (firstChar < ('a' as char) || 
                        firstChar > ('z' as char)) {

                        containsReposWithInvalidFirstChar = true
                    }
                } else {
                    unfixableRepoNames << repoName
                }
            }
            if (!longRepoPath) { 
                def repoPath = new File(repoParent, repoName).absolutePath
                if (repoPath.length() > CTF_REPO_PATH_LIMIT) {
                    longRepoPath = repoPath                    
                }                
            }
        }
        return ['unfixableRepoNames':unfixableRepoNames, 
                'duplicatedReposIgnoringCase':duplicatedReposIgnoringCase,
                'containsUpperCaseRepos':containsUpperCaseRepos, 
                'containsReposWithInvalidFirstChar': 
                containsReposWithInvalidFirstChar,
                'longRepoPath':longRepoPath]
    }

    String validateRepoPrefix(prefix) {
        String conflict = null
        def repoNames = Repository.list().collect { it.name.toLowerCase() }
        HashSet nameSet = new HashSet(repoNames)
        for (repo in repoNames) {
            String tmp = prefix + repo.toLowerCase()
            if (nameSet.contains(tmp)) {
                conflict = tmp
                break
            }
        }
        conflict
    }

    /**
     * @param conversionData is the wizard bean.
     * @return the list of users from CSVN compared to the remote TeamForge
     * server's. 
     * @throws RemoteMasterException in case the call to the TeamForge server
     * to retrieve users fail.
     */
    List<List<String>> getCsvnUsersComparedToCtfUsers(conversionData) {
        def ctfRemoteUsers = ctfRemoteClientService.getUserList(
            conversionData.ctfURL, conversionData.userSessionId)
        def ctfUsers = new HashSet(ctfRemoteUsers.toList().collect { 
            it.userName })
        def csvnUsers = User.list(sort:"username")
        List<String> ctfUsernames = new ArrayList<String>(csvnUsers.size())
        List<String> csvnOnlyUsernames = new ArrayList<String>(csvnUsers.size())
        for (user in csvnUsers) {
            if (ctfUsers.contains(user.username)) {
                ctfUsernames << user.username
            } else {
                csvnOnlyUsernames << user.username
            }
        }
        [ctfUsernames, csvnOnlyUsernames]
    }
    
    /**
     * Will try to connect to the CTF instance and if successful will fill in the version information
     * for the conversionData object.  If unable to connect, conversionData.errorMessage may contain
     * useful information
     */
    boolean confirmConnection(conversionData) {
        boolean success = false
        try {
            def ctfSoap = ctfRemoteClientService.cnSoap(conversionData.ctfURL)
            conversionData.userSessionId = ctfRemoteClientService.login(
                conversionData.ctfURL, conversionData.ctfUsername,
                conversionData.ctfPassword)
            conversionData.apiVersion = ctfSoap.getApiVersion()
            conversionData.appVersion = ctfSoap.getVersion(getUserSessionId(
                conversionData))
            // Calling this here because we may not keep the user's credentials
            // after this point and we'll need the web session id to revert
            getWebSessionId(conversionData)
            success = true
        } catch (Exception e) {
            while (e.cause != null) {
                e = e.cause
            }

            if (e.message) {
                conversionData.errorMessage = e.message
            } else {
                conversionData.errorMessage = "Encountered an exception " +
                    "while trying to connect: " + e.getClass().name
            }
        }
        return success
    }
    
    private String encodeParameters(paramMap) {
        StringBuilder buf = new StringBuilder();
        for (def entry : paramMap.entrySet()) {
            buf.append('&').append(URLEncoder.encode(entry.key))
                .append('=').append(URLEncoder.encode(entry.value))
        }
        buf.substring(1)
    }        
    
    private void writeParameters(paramMap, conn) {
        conn.outputStream.withWriter "UTF-8", {
            it << encodeParameters(paramMap)
        }
    }
    
    protected HttpURLConnection openPostUrl(String url, def paramMap, boolean followRedirect) {
        HttpURLConnection conn = 
            (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST")
        conn.setRequestProperty("Accept-Language", 'en');
        conn.setRequestProperty("Content-Type", 
            "application/x-www-form-urlencoded; charset=UTF-8");
        conn.setInstanceFollowRedirects(followRedirect)
        conn.setDoOutput(true)
        conn.setUseCaches(false);
        writeParameters(paramMap, conn)
        conn
    }
    

    /**
     * @param ctfUrl is a ctf url, containing the protocol + hostname + port
     * @param ctfUsername is an existing username in the given ctfUrl
     * @param ctfPassword is a password associated with the given username.
     * @return Map with the keys [jsessionid, usessionid] after logging in
     * into the given ctfUrl with the given ctfUsername and ctfPassword
     */
    public def loginCtfWebapp(ctfUrl, ctfUsername, ctfPassword) {
        return this.getCtfSessionIds(ctfUrl, ctfUsername, ctfPassword)
    }

    protected loginCtfWebapp(conversionData) {
        return this.getCtfSessionIds(conversionData.ctfURL,
            conversionData.ctfUsername, conversionData.ctfPassword)
    }

    /**
     * @param ctfUrl the main ctf url.
     * @return a login URL for a given CTF URL
     */
    public String buildCtfLoginUrl(ctfUrl) {
        return ctfUrl + "/sf/sfmain/do/login"
    }

    /**
     * @param ctfUrl is a ctf url, containing the protocol + hostname + port
     * @param ctfUsername is an existing username in the given ctfUrl
     * @param ctfPassword is a password associated with the given username.
     * @return Map with the keys [jsessionid, usessionid] after logging in
     * into the given ctfUrl with the given ctfUsername and ctfPassword
     */
    private def getCtfSessionIds(ctfUrl, ctfUsername, ctfPassword) {
        def requestParams = [username: ctfUsername, password: ctfPassword,
             sfsubmit: "submit"]
        def conn = openPostUrl(this.buildCtfLoginUrl(ctfUrl), requestParams, 
                false)

        this.debugHeaders(conn)

        def headers = conn.headerFields
        def cookieHeaders = headers.get("Set-Cookie")
        if (!cookieHeaders) {
            cookieHeaders = headers.get("set-cookie")
        }
        def sessionid = [jsessionid: null, usessionid: null]
        if (cookieHeaders) {
            cookieHeaders.each {
                def cookies = HttpCookie.parse(it)
                cookies.each { cookie ->
                    if (cookie.name == 'sf_auth') {
                        def v = cookie.value
                        def amp = v.indexOf('&')
                        sessionid.jsessionid = v.substring(amp + 1)
                        sessionid.usessionid = v.substring(0, amp)
                    }
                }
            }
        } else {
            log.debug "No cookies set in the response."
        }
        
        /*
        if (!sessionid.jsessionid) {
            // Added this code when making a mistake wrt redirects. Shouldn't 
            // be exercised, with followRedirect = false
            String page =  conn.inputStream.text
            jsessionid = page.find(~/jsessionid=(\w+)/, { match, id -> id })
            if (!jsessionid) {
                conn.disconnect()
                throw new Exception("login didn't work")
            }
        }
        */
        conn.disconnect()
        sessionid
    }

    /**
     * Register this svnedge instance as an integration server on TeamForge
     * @param conversionData is the data to be used for the conversion.
     * @return the systemId of the registration.
     * @throws CtfAuthenticationException if the authentication fails with 
     * TeamForge.
     * @throws RemoteMasterException if any error occurs during the method 
     * call.
     */
    protected def registerIntegrationServer(conversionData) {

        def server = Server.getServer()

        def adapterType = "Subversion"
		def appServerPort = conversionData.consolePort
        def title = "CollabNet Subversion Edge (${server.hostname}:" +
                "${appServerPort})"
        def description = "This is a CollabNet Subversion Edge " +
                "server in managed mode from ${server.hostname}:" +
                "${appServerPort}."

        // SSL for integration and viewvc apps is based on Jetty's
        // configuration, not Apache/SVN (aka "server")
        def useSSL = conversionData.consoleSsl ? "true" : "false"
        def names = [ "RequireApproval", "HostName", "HostPort", "HostSSL", 
                "isCSVN", "ScmViewerUrl", "RepositoryBaseUrl", 
                "RepositoryRootPath" ] 
        
        def repositoryPath = operatingSystemService.isWindows() ? "/windows-scmroot" : server.repoParentDir
        
        // CTF assumes RepositoryBaseUrl does not include trailing slash --
        // removing from submitted value if present
        String svnUrl = server.svnURL();
        if (svnUrl.endsWith("/")) {
            svnUrl =  svnUrl.substring(0, svnUrl.length() - 1) 
        }
        def values = [ "false", server.hostname, "${appServerPort}", useSSL, 
                "true", getViewVcUrl(server, server.useSsl), svnUrl,
                repositoryPath ];
        def props = ctfRemoteClientService.makeSoapNamedValues(names, values)

        def uSessionId = getUserSessionId(conversionData)
        def systemId = ctfRemoteClientService.addExternalSystem(
            conversionData.ctfURL, uSessionId, adapterType, title, description,
            props)

        return systemId
    }

    protected def getViewVcUrlPrefix(server, useSsl) {
        def port = useSsl ? 
            ((server.port == 443) ? "" : ":" + server.port) :
            ((server.port == 80) ? "" : ":" + server.port)
        (useSsl ? "https" : "http") +
                "://" + server.hostname + port
    }
    
    protected def getViewVcUrl(server, useSsl) {
        getViewVcUrlPrefix(server, useSsl) + "/viewvc"
    }

    protected void debugHeaders(conn) {
        String headerfields = conn.getHeaderField(0);
        log.debug headerfields
        log.debug "---Start of headers---"
        int i = 1;
        while ((headerfields = conn.getHeaderField(i)) != null) {
            String key = conn.getHeaderFieldKey(i);
            log.debug(((key == null) ? "" : key + ": ") + headerfields)
            i++;    
        }
    }

    protected void fixInvalidRepos(conversionData) {
        def repos = Repository.list()
        for (repoDomain in repos) {
            def repo = repoDomain.name
            if (!repoDomain.validateName()) {
                if (repo.matches(FIXABLE_REPO_NAME)) {
                    def newRepo = repo
                    if (conversionData.lowercaseRepos) {
                        newRepo = newRepo.toLowerCase()
                    }     
                    char firstChar = newRepo.charAt(0)
                    if (conversionData.repoPrefix && 
                        (firstChar < ('a' as char) || 
                        firstChar > ('z' as char))) {
                        newRepo = conversionData.repoPrefix + newRepo
                    }
                    def newRepoDomain = new Repository(name:newRepo)
                    newRepoDomain.discard()
                    if (newRepo == repo || !newRepoDomain.validateName()) {
                         throw new Exception(
                            "Invalid CTF repository name: " + newRepo)
                    } else {
                        def parentDir = Server.getServer().repoParentDir
                        File f1 = new File(parentDir, repo)
                        File f2 = new File(parentDir, newRepo)
                        if (f2.exists()) {
                            throw new Exception(
                                "Renaming '" + repo + 
                                "' failed because '" + newRepo + 
                                "' already exists.")
                        } else if (!f1.renameTo(f2)) {
                            throw new Exception(
                                "Unexpected failure renaming '" + repo + 
                                "' as '" + newRepo + "'")
                        } else {
                            repoDomain.name = newRepo
                            repoDomain.save()
                        }
                    }
                } else {
                    throw new Exception(
                        "Invalid CTF repository name: " + repo + 
                        " could not be fixed.")
                }
            } 
        }
    }

    protected void addReposToProjects(conversionData) {
        if (conversionData.isProjectPerRepo) {
            throw new UnsupportedOperationException("Not implemented")
        } else {
            addReposToSingleProject(conversionData)
        }
    }

    private String projectUrl(String projectName) {
        return projectName.toLowerCase().replace(' ', '_')
    }

    private void addReposToSingleProject(conversionData) 
        throws RemoteMasterException{

        def projectName = conversionData.ctfProject
        def projectPath = projectUrl(projectName)
        def projects = ctfRemoteClientService.getProjectList(
            conversionData.ctfURL, conversionData.userSessionId)
        String projectId = null
        for (p in projects) {
            if (projectName.toLowerCase() == p.title.toLowerCase() || 
                projectPath == p.path.substring(9)) {
                projectId = p.id
                break
            }
        }
        
        if (!projectId) {
            log.info("Creating new project '" + projectName + 
                "' in CTF to hold existing repositories")
            def ctfSoap = ctfRemoteClientService.cnSoap(conversionData.ctfURL)
            //TODO: move this method call to ctfRemoteClientService
            def p = ctfSoap.createProject(getUserSessionId(conversionData), 
                projectPath, projectName, "Container for repositories " +
                "existing prior to CollabNet Subversion Edge conversion to " +
                "managed mode")
            projectId = p.id
        }
        
        def systemId = conversionData.exSystemId
        def uSessionId = getUserSessionId(conversionData)
        def scmSoap = ctfRemoteClientService.makeScmSoap(conversionData.ctfURL)
        boolean idRequiredOnCommit = false
        boolean hideMonitoringDetails = false
        def desc = "Added from existing CollabNet Subversion Edge repository"
        def repos = Repository.list()
        if (log.isDebugEnabled()) {
            log.debug "repos=" + repos
        }
        String repoParentDir = Server.getServer().repoParentDir
        for (repo in repos) {
            File repoDir = new File(repoParentDir, repo.name)
            File hooksDir = new File(repoDir, "hooks")
            archiveCurrentHooks(hooksDir)            

            //TODO: move this method call to ctfRemoteClientService
            scmSoap.createRepository2(uSessionId, projectId, systemId, 
                repo.name, repo.name, desc, idRequiredOnCommit, 
                hideMonitoringDetails, desc) 
        }
        conversionData.ctfProjectId = projectId
    }
        
    def boolean isFreshInstall() {
        Repository.count() == 0
    }

    def convert(conversionData) {
        def errors = []
        def warnings = []

        def ctfServer = CtfServer.getServer() 
        if (!ctfServer) {
            ctfServer = new CtfServer()
        }
        ctfServer.baseUrl = conversionData.ctfURL 
        ctfServer.internalApiKey = conversionData.serverKey
        ctfServer.save(flush:true)

        // TODO shutdown access to svn, put up a static conversion
        // page until done.
        def server = Server.getServer()
        server.mode = ServerMode.CONVERTING_TO_MANAGED
        server.save(flush:true)

        def exception = null
        try {
            if (this.isFreshInstall()) {
                conversionData.userSessionId = ctfRemoteClientService.login(
                    conversionData.ctfURL, conversionData.ctfUsername, 
                    conversionData.ctfPassword)
            }
            doConvert(conversionData, ctfServer, server, errors, warnings)

        } catch (UnknownHostException unknownHost) {
            exception = unknownHost

        } catch (NoRouteToHostException unreachableServer) {
            exception = unreachableServer

        } catch (CantBindPortException cantRestartServer) {
            if (!this.isFreshInstall()) {
                this.undoLocalServerConfiguration(server)
            }
            exception = cantRestartServer

        } catch (CtfAuthenticationException wrongCredentials) {
            if (!this.isFreshInstall()) {
                this.undoLocalServerConfiguration(server)
            }
            exception = wrongCredentials

        } catch (RemoteMasterException scmMightNotBeReachable) {
            // this might happen because ViewVC or Subversion URLs are not
            // reachable for some reason. Throw as general error. Also
            // login problems, or parameters that are wrong.
            // At this point, only the configuration and the server state has
            // been changed. So, change it back instead of a full revert.
            if (!this.isFreshInstall()) {
                this.undoLocalServerConfiguration(server)
            }
            exception = scmMightNotBeReachable

        } catch (Exception e) {
            log.error("CTF mode conversion failed: " + e.message)
            if (e.message) {
                errors << "An exception occured: " + e.message
            } else {
                errors << "An exception occured: " + e.getClass().name
            }
        }

        if (errors && !exception) {
            try {
                doRevertFromCtfMode(conversionData.ctfURL,
                    conversionData.exSystemId,
                    getWebSessionId(conversionData), 
                    server, ctfServer, errors)
            } catch (Exception e) {
                def msg = "Recovery from failed CTF mode conversion " +
                    "failed. Check logs for details."
                log.error(msg, e)
                errors << msg
            }
        }
        return ['errors':errors, 'warnings':warnings, 'exception': exception]
    }

    private def doConvert(conversionData, ctfServer, server, errors, warnings) {
        installIntegrationServer(conversionData)
        unpackIntegrationScripts()

        serverConfService.backupAndOverwriteHttpdConf()
        serverConfService.writeConfigFiles()
        restartServer()

        // Noticing a tendency for the viewvc URL validation during 
        // conversion to fail once and then work the second try.  This 
        // seemed to help, so leaving it until more investigation is done.
        Thread.sleep(1000)

        conversionData.exSystemId = registerIntegrationServer(conversionData)
        ctfServer.mySystemId = conversionData.exSystemId
        ctfServer.save(flush:true)

        // if no errors are encountered during the initial conversion, then
        // proceed with the conversion.

        if (conversionData.ctfProject) {
            fixInvalidRepos(conversionData)
            addReposToProjects(conversionData)
        }

        server.mode = ServerMode.MANAGED
        server.save(flush:true)
        restartServer()

        authenticationManager.providers = [ctfAuthenticationProvider]

        if (conversionData.ctfProject && conversionData.importUsers
            && !errors) {
            addUsers(conversionData, warnings)
        }

        log.info("Registered external system ID '${conversionData.exSystemId}'")
    }

    private logMsg(def msg, def errors, Exception e) {
            GrailsUtil.deepSanitize(e)
            log.warn(msg, e)
            errors << msg
    }

    private void undoLocalServerConfiguration(Server server) {
        server.mode = ServerMode.STANDALONE
        server.save(flush:true)
        serverConfService.restoreHttpdConfFromBackup() 
        serverConfService.writeConfigFiles()
        try {
            restartServer()
        } catch (CantBindPortException cantBind) {

        }
    }

    /**
     * Sets the server to be in stand-alone mode, as well as removes the 
     * ctfServer instance.
     * 
     * @param server is the current server
     * @param ctfServer is the current ctf server
     * @param errors is the errors to be collected.
     */
    private void undoServers(Server server, CtfServer ctfServer, errors) {
        try {
            if (ctfServer && ctfServer.mySystemId || server) {
                this.undoLocalServerConfiguration(server)
            }

        } catch (Exception e) {
            def msg = "Recovery from failed CTF mode conversion " +
                "failed. Subversion server configuration was not reverted."
            logMsg(msg, errors, e)
            errors << msg
        }

        if (ctfServer) {
            ctfServer.delete()
        }

        authenticationManager.providers = [daoAuthenticationProvider, 
                anonymousAuthenticationProvider]
    }

    private void undoLocalRepositoriesDependencies(Server server, errors) {
        File repoParent = new File(server.repoParentDir)
        try {
            File idFile = new File(repoParent, ".scm.properties")
            if (idFile.exists() && !idFile.delete()) {
                def msg = "While recovering from failed conversion, the " +
                     "external system property file was not deleted."
                logMsg(msg, errors, e)
            }
        } catch (Exception e) {
            def msg = "While recovering from failed conversion, the " +
                 "external system property file was not deleted."
            logMsg(msg, errors, e)
        }

        try {
            repoParent.eachFile {
                File hooksDir = new File(it, "hooks")
                if (hooksDir.exists()) {
                    restoreNonCtfHooks(hooksDir)
                }
            }
        } catch (Exception e) {
            def msg = "Recovery from failed CTF mode conversion " +
                "failed. Some hook scripts were not reverted."
            logMsg(msg, errors, e)
        }
    }

    /**
     * Deletes an external system in CTF using the Web UI
     * 
     * @param ctfUrl is the ctf url
     * @param jsessionid is the jsessionid of a current opened session
     * @param externalSystemId is the external system id to be removed
     * @param errors is the collection of errors to be collected.
     * 
     * @return boolean if the system id has been deleted
     */
    private undoExternalSystemOnRemoteCtfServer(ctfUrl, jsessionid, 
            externalSystemId, errors) {

        def delUrl = ctfUrl + "/sf/sfmain/do/selectSystems;jsessionid=" + 
            jsessionid
        def formParams = [sfsubmit: "delete", _listItem: externalSystemId]
        def conn = openPostUrl(delUrl, formParams, true) // follow redirects

        log.debug "response from deleting integration server"
        debugHeaders(conn)

        String page =  conn.inputStream.text
        def systemId = page.find('value="(exsy' + externalSystemId + ')"', 
                { match, id -> id })
        def exceptionIndex = page.indexOf("A TeamForge system error has occurred")
        if (systemId || exceptionIndex >= 0) {
            log.warn "Delete from CTF did not succeed\n\n" + page
            errors << "After incomplete conversion, removal of this" +
                " integration server from TeamForge failed."
            return false
        } else {
            return true
        }
    }

    /**
     * Converts the current instance back to SvnEdge stand-alone. If the current
     * server is already converted, then the given admin username/password is
     * used to login to the ctfUrl currently installed.
     * 
     * @param ctfUsername is the username for the ctf admin.
     * @param ctfPassword is the password for the ctf admin.
     * @param errors the errors to be collected.
     * @throws CtfAuthenticationException if the authentication fails with the
     * CTF server.
     */
    protected void revertFromCtfMode(String ctfUsername, String ctfPassword,
            errors) throws CtfAuthenticationException {

        Server server = Server.getServer()
        CtfServer ctfServer = CtfServer.getServer()

        if (ctfServer?.baseUrl) {
            def sessionIds = this.loginCtfWebapp(ctfServer.baseUrl, ctfUsername,
                ctfPassword)
            doRevertFromCtfMode(ctfServer.baseUrl, ctfServer.mySystemId,
                sessionIds?.jsessionid, server, ctfServer, errors)
        }
    }

    private void doRevertFromCtfMode(ctfUrl, exSystemId, jsessionid, 
                                     server, ctfServer, errors) {
        if (jsessionid) {
            this.undoServers(server, ctfServer, errors)
            if (ctfUrl && exSystemId) {
                this.undoExternalSystemOnRemoteCtfServer(
                    ctfUrl, jsessionid, exSystemId, errors)
            }
            this.undoLocalRepositoriesDependencies(server, errors)
        } else {
            throw new CtfAuthenticationException("Unable to confirm the " +
                "credentials. Error logging in. Please verify the " +
                "username and password.")
        }
    }

    private static final String sfPrefix = 
        "# BEGIN SOURCEFORGE SECTION - Do not remove these lines\n"
    private static final String sfSuffix = "# END SOURCEFORGE SECTION\n"

    private static final String NON_CTF_HOOKS_ARCHIVE = "pre-ctf-hooks.zip"

    protected void archiveCurrentHooks(File hooksDir) {
        archiveFiles(NON_CTF_HOOKS_ARCHIVE, hooksDir, 
                     [CTF_HOOKS_ARCHIVE, OLD_CTF_HOOKS_ARCHIVE])
    }

    protected void archiveFiles(String zipFileName, File directory, 
        List<String> excludedFiles=null) {
        log.debug("Archiving files in " + directory.name + 
                  " into " + zipFileName)
        File zipFile = new File(directory, zipFileName)
        boolean filesExist = false
        directory.eachFile { f ->
            if (isFileToBeArchived(f, zipFile, excludedFiles)) {
                filesExist = true
            }
        }
        if (!filesExist) {
            log.debug("No files to archive")
            return
        }
        
        ZipOutputStream zos = null
        try {
            zos = new ZipOutputStream(zipFile.newOutputStream())
            directory.eachFile { f ->
                if (isFileToBeArchived(f, zipFile, excludedFiles)) {
                    ZipEntry ze = new ZipEntry(f.name)
                    if (f.canExecute()) {
                        ze.extra = [1] as byte[]
                    }
                    zos.putNextEntry(ze)
                    f.withInputStream { zos << it }
                    zos.closeEntry()
                }
            }
        } finally {
            if (zos) {
                zos.close()
            }
        }

        directory.eachFile { f ->
            if (isFileToBeArchived(f, zipFile, excludedFiles)) {
                log.debug("Deleting file: " + f.name)
                f.delete()
            }
        }
    }

    private boolean isFileToBeArchived(File f, File zipFile, List<String> excludedFiles) {
        return f.isFile() && !f.equals(zipFile) && 
            (!excludedFiles || !excludedFiles.contains(f.name))
    }
    
    private static final String INTEGRATION_SCRIPTS_ZIP =
        "integration-scripts.zip"

    protected unpackIntegrationScripts() {
        def libDir = new File(lifecycleService.appHome, "lib")
        def archiveFile = new File(libDir, INTEGRATION_SCRIPTS_ZIP)
        def integrationDir = new File(libDir, "integration")
        if (integrationDir.exists() && !integrationDir.deleteDir()) {
            log.warn("Unable to remove integration directory before " + 
                "unpacking up-to-date integration scripts.")
        }
        unpackZipFile(archiveFile, libDir, {a, b -> })
        File oldViewvcFile = new File(libDir, 
            "integration/viewvc/lib/vcauth/teamforge")
        File newViewvcFile = 
            new File(libDir, "viewvc/vcauth/teamforge")
        if (newViewvcFile.exists() && !newViewvcFile.deleteDir()) {
            log.warn("Unable to delete existing ViewVC teamforge " + 
                "authorizer: " + newViewvcFile.absolutePath)
        }
        if (!oldViewvcFile.renameTo(newViewvcFile)) {
            throw new Exception("Rename did not work; unable to " + 
                "setup vcauth.")
        } else {
            oldViewvcFile.parentFile.parentFile.deleteDir()
        }

        oldViewvcFile = new File(libDir, 
            "integration/viewvc/bin/mod_python/ctf_handler.py")
        if (!oldViewvcFile.exists()) {
            throw new FileNotFoundException("missing file " + oldViewvcFile)
        }
        newViewvcFile = new File(lifecycleService.appHome, 
                                 "bin/mod_python/viewvc_ctf_handler.py")
        if (newViewvcFile.exists() && !newViewvcFile.delete()) {
            log.warn("Unable to delete existing ViewVC teamforge " + 
                "handler: " + newViewvcFile.absolutePath)
        }
        if (!oldViewvcFile.renameTo(newViewvcFile)) {
            throw new Exception("Rename did not work; unable to " + 
                "setup viewvc python handler at " + newViewvcFile)
        } else {
            oldViewvcFile.parentFile.parentFile.parentFile.deleteDir()
        }
    }

    protected unpackZipFile(File archiveFile, File destDir, 
                            Closure fileEntryClosure) {
        ZipFile zipFile = null
        try {
            // Open Zip file for reading
            zipFile = new ZipFile(archiveFile)
            Enumeration<ZipEntry> zipFileEntries = zipFile.entries()
            while (zipFileEntries.hasMoreElements())
            {
                ZipEntry entry = zipFileEntries.nextElement()
                if (entry.isDirectory()) {
                    new File(destDir, entry.name).mkdirs()
                } else {
                    File destFile = new File(destDir, entry.getName())
                    BufferedInputStream is = null
                    try {
                        is = new BufferedInputStream(
                            zipFile.getInputStream(entry));
                        destFile.withOutputStream { it << is }
                    } finally {
                        if (is) {
                            is.close()
                        }
                    }
                    fileEntryClosure(entry, destFile)
                }
            }
        } finally {
            if (zipFile) {
                zipFile.close()
            }
        }
    }

    private static final String CTF_HOOKS_ARCHIVE = "ctf-hook-scripts.zip"
    private static final String OLD_CTF_HOOKS_ARCHIVE = 
        "old-ctf-hook-scripts.zip"
    
    protected void restoreNonCtfHooks(File hooksDir) {
        log.debug("Archiving CTF hook scripts and restoring pre-ctf " + 
                  "scripts for " + hooksDir.name)
        File ctfHooksZip = new File(hooksDir, CTF_HOOKS_ARCHIVE)
        if (ctfHooksZip.exists()) {
            File oldCtfHooksZip = new File(hooksDir, OLD_CTF_HOOKS_ARCHIVE)
            if (!oldCtfHooksZip.exists() || oldCtfHooksZip.delete()) {
                log.debug("Previous CTF archive is being moved.")
                ctfHooksZip.renameTo oldCtfHooksZip
            }
        }

        log.debug("Archiving CTF hook scripts")
        archiveFiles(CTF_HOOKS_ARCHIVE, hooksDir, 
                     [NON_CTF_HOOKS_ARCHIVE, OLD_CTF_HOOKS_ARCHIVE])

        File archiveFile = new File(hooksDir, NON_CTF_HOOKS_ARCHIVE)
        if (archiveFile.exists()) {
            log.debug("Restoring pre-ctf hook scripts")
            unpackZipFile(archiveFile, hooksDir, {entry, destFile -> 
                byte[] extra = entry.extra
                if (extra && extra.length == 1 && extra[0] == 1) {
                    destFile.setExecutable(true)
                }
            })
            archiveFile.delete()
        }
    }

    private String getContentPrefix(String script) {
        String sourceforgeHome = "./FIXME" // FIXME!
        File pythonScriptDir = new File(sourceforgeHome, "integration")
        sfPrefix + "python " + 
            new File(pythonScriptDir, script).canonicalPath
    }

    def installIntegrationServer(conversionData) {
        // TODO
        // will need to copy conversionData.serverKey to the integration
        // server configuration.  For example in a sourceforge.properties
        // file:
        // sfmain.integration.security.shared_secret=${conversionData.serverKey}

    }

    protected def addUsers(conversionData, warnings) {
        try {
            def csvnUsernames = 
                getCsvnUsersComparedToCtfUsers(conversionData)[1]

            def csvnUsers = User.list()
            for (User user in csvnUsers) {
                if (csvnUsernames.contains(user.username)) {
                    addUser(user, conversionData, warnings)
                }
            }
        } catch (Exception e) {
            def msg = "An error occured and one or more users were not " +
                "imported into CollabNet TeamForge. " + 
                "Check logs for details."
            logMsg(msg, warnings, e)
        }
    }

    protected def addUser(User u, conversionData, warnings) {
        boolean isSuperUser = false
        boolean isRestrictedUser = false
        String password = securityService.generatePassword(8, 12)
        def userDO = null
        try {
            userDO = ctfRemoteClientService.createUser(conversionData.ctfURL,
                getUserSessionId(conversionData), u.username, password, u.email,
                u.realUserName, isSuperUser, isRestrictedUser)

            if (userDO && conversionData.assignMembership) {
                //TODO: expose the method call addProjectMemeber to the service
                def ctfSoap = ctfRemoteClientService.cnSoap(
                    conversionData.ctfURL)
                ctfSoap.addProjectMember(
                    getUserSessionId(conversionData), 
                    conversionData.ctfProjectId,
                    userDO.username)
            }
        } catch (com.collabnet.ce.soap50.fault.NoSuchObjectFault e) {
            log.warn("Error while assigning project membership to " + 
                     u.username, e)
            warnings << "User '" + u.username + "' was not added as a " + 
                "member of the project."

        } catch (RemoteMasterException remoteProblems) {
            log.error("Error while assigning project membership to " + 
                     u.username, remoteProblems)
            warnings << remoteProblems.getMessage()
        }
    }

    private def restartServer() {
        if (lifecycleService.isStarted()) {
            return lifecycleService.gracefulRestartServer()
        } else {
            return lifecycleService.startServer()
        }
    }

    private void copyFiles(fromDir, toDir) {
        fromDir.eachFile {
            if (it.isFile()) {
                copyFile(it, new File(toDir, it.name))
            }
        }
    }       

    private void copyFile(f1, f2) {
        InputStream ins = new FileInputStream(f1);
        OutputStream outs = new FileOutputStream(f2);
      try {

        byte[] buf = new byte[1024];
        int len;
        while ((len = ins.read(buf)) > 0){
            outs.write(buf, 0, len);
        }
      }
      finally {
        ins.close();
        outs.close();
      }
    }
}
