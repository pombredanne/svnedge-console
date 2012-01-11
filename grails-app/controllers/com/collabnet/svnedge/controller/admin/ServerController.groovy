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
package com.collabnet.svnedge.controller.admin

import org.codehaus.groovy.grails.commons.ConfigurationHolder
import org.codehaus.groovy.grails.plugins.springsecurity.Secured

import org.springframework.web.servlet.support.RequestContextUtils as RCU

import com.collabnet.svnedge.CantBindPortException;
import com.collabnet.svnedge.domain.MailConfiguration;
import com.collabnet.svnedge.domain.Server 
import com.collabnet.svnedge.domain.ServerMode 
import com.collabnet.svnedge.domain.integration.CtfServer 
import com.collabnet.svnedge.domain.integration.ReplicaConfiguration 
import com.collabnet.svnedge.integration.CtfAuthenticationException;
import com.collabnet.svnedge.util.ConfigUtil
import com.collabnet.svnedge.domain.NetworkConfiguration;

import grails.converters.JSON

import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Secured(['ROLE_ADMIN', 'ROLE_ADMIN_SYSTEM'])
class ServerController {

    private static final String TEST_MAIL_RESULT = "email.test.result"
    private static final long TEST_MAIL_WAIT_SECONDS = 10

    def operatingSystemService
    def lifecycleService
    def mailNotificationService
    def networkingService
    def serverConfService
    def setupTeamForgeService
    def setupReplicaService
    def csvnAuthenticationProvider
    def securityService
    def ctfRemoteClientService
    def packagesUpdateService

    def index = { redirect(action:edit, params:params) }

    // the delete, save and update actions only accept POST requests
    static allowedMethods = [update:'POST', revert: 'POST']

    def revert = { CtfCredentialCommand ctfCredentials ->
        ctfCredentials.validate()
        def ctfServer = CtfServer.getServer()
        def server = Server.getServer()
        boolean isReplica = (server.mode == ServerMode.REPLICA)
        String errorView = "editIntegration"
        
        if (ctfCredentials.hasErrors()) {
            def formError = message(code: 
                "server.action.revert.error.credentials")
            render(view: errorView,
                    model: [ctfServerBaseUrl: CtfServer.getServer().baseUrl,
                        ctfCredentials: ctfCredentials, formError: formError])
        } else {
            try {
                def errors = []
                if (isReplica) {
                    this.setupReplicaService.revertFromReplicaMode(
                        ctfCredentials.ctfUsername, ctfCredentials.ctfPassword,
                        errors, RCU.getLocale(request))
                }
                else {
                    this.setupTeamForgeService.revertFromCtfMode(
                        ctfCredentials.ctfUsername, ctfCredentials.ctfPassword,
                        errors, RCU.getLocale(request))
                }
                
                if (errors) {
                    def formError = 
                        message(code: "server.action.revert.error.general")
                    render(view: errorView,
                        model: [ctfServerBaseUrl: ctfServer.baseUrl,
                            ctfCredentials: ctfCredentials,
                            formError: formError, errorCause: errors])
                } else {
                    flash.message = message(code: 
                        "server.action.revert.success")
                    redirect(controller: "status", action: "index")
                }
            } catch (CtfAuthenticationException wrongCredentials) {
                def ctfBaseUrl = ctfServer.baseUrl
                def formError = message(code:
                    "server.action.revert.error.connection", args: [ctfBaseUrl])
                render(view: errorView,
                        model: [ctfServerBaseUrl: ctfBaseUrl,
                            ctfCredentials: ctfCredentials,
                            formError: formError, 
                            errorCause: wrongCredentials.getMessage()])
            }
        }
    }

    def editIntegration = {
        flash.warn = message(code: "server.action.revert.warn")
        def ctfCredentialsCmd = new CtfCredentialCommand()
        Server s = Server.getServer()

        return [ctfServerBaseUrl: CtfServer.getServer()?.baseUrl,
                ctfCredentials: ctfCredentialsCmd]
    }

    def edit = {
        def server = Server.getServer()
        prepareServerViewModel(server)

    }

    def editAuthentication = {
        def server = Server.getServer()
        if (!server.authHelperPort) {
           server.authHelperPort =     
                csvnAuthenticationProvider.getAuthHelperPort(server, server.ldapEnabled) as Integer
        }
        return [server: server,
                csvnConf: ConfigUtil.confDirPath(),
                isConfigurable: serverConfService.createOrValidateHttpdConf()
        ]
    }
    
    def editProxy = {
       def networkConfig = networkingService.getNetworkConfiguration()
       return [networkConfig: networkConfig]
    }
    
    def updateProxy = {
        // remove the network config when the fields are completely empty
        if (!params.httpProxyHost && !params.httpProxyPort &&
                !params.httpProxyUsername && !params.httpProxyPassword) {
            forward(action: "removeProxy")
        }        
        // find or create NetworkConfiguration instance
        def networkConfig = networkingService.getNetworkConfiguration() ?: new NetworkConfiguration()
        // bind data, excluding password         
        bindData(networkConfig, params, ['httpProxyPassword'])
        // if a new password has been input, copy to the entity 
        if (params['httpProxyPassword_changed'] == 'true') {
            networkConfig.httpProxyPassword = params['httpProxyPassword']
        }     
        networkConfig.validate()
        if (!networkConfig.hasErrors() && networkingService.saveNetworkConfiguration(networkConfig)) {
            serverConfService.writeConfigFiles()
            ctfRemoteClientService.clearClientCache()
            packagesUpdateService.setProxyFromSystemEnvironment()
            flash.message = message(code:"server.action.updateProxy.success")
            redirect(action: editProxy)
        }
        else {
            networkConfig.discard()
            request.error = message(code:"server.action.update.invalidSettings")
            render(view: "editProxy", model: [networkConfig: networkConfig])
        }
    }
    
    def removeProxy = {
        networkingService.removeNetworkConfiguration() 
        serverConfService.writeConfigFiles()
        ctfRemoteClientService.clearClientCache()
        packagesUpdateService.setProxyFromSystemEnvironment()
        flash.message = message(code:"server.action.updateProxy.removed")
        redirect(action: editProxy)
     }

    def update = {
        flash.clear()
        def server = Server.getServer()
        params.each{ key, value -> 
            if (params[key] instanceof String) {
                params[key] = ((String)(params[key])).trim()
            }
        }
        if (params.version) {
            def version = params.version.toLong()
            if (server.version > version) {
                server.errors.rejectValue("version",
                    "server.optimistic.locking.failure")
                    render(view:'edit', model:[server:server])
                    return
            }
        }

        // checking for port error before params are applied
        // since changes to Server seem to persist automatically with
        // call to service methods
        boolean portError = false
        if (params.port?.isNumber() && params.port.toInteger() < 1024 ) {
            lifecycleService.clearCachedResults()
            if (!lifecycleService.isDefaultPortAllowed()) {
                portError = true
            }
        }
        
        // copy form params to the entity, excluding ldapAuth password
        bindData(server, params, ['ldapAuthBindPassword', 'ldapServerHost'])

        // if a new password has been input, copy to
        // the server entity
        if (params['ldapAuthBindPassword_changed'] == 'true') {
            server.ldapAuthBindPassword = params['ldapAuthBindPassword']
        }                                       

        // ensure ldapServerHost is just ldapServerHost no extra stuff like 
        //ldap:// or ldaps://
        if (params['ldapServerHost']) {
            int pos = params['ldapServerHost'].indexOf("://")
            if (pos != -1) {
                server.ldapServerHost = params['ldapServerHost'].substring(pos+3)
            } else {
                server.ldapServerHost = params['ldapServerHost']
            }
        }

        //In editAuthentication UI repoParentDir does not exist in Params.
        if (params.repoParentDir != null) {
          // canonicalize repo parent dir (esp to remove trailing "/" or "\"
          // that server.validate would allow)
          String repoParent = new File(params.repoParentDir).canonicalPath
          server.repoParentDir = repoParent
        }

        // validate, and reject port value if needed
        server.validate()
        if (portError) {
            server.errors.rejectValue("port",
                "server.port.defaultValue.rejected")
        }

        if(!server.hasErrors() && server.save(flush:true)) {
            if (lifecycleService.isStarted()) {
                try {
                    def result = lifecycleService.stopServer()
                    if (result <= 0) {
                        result = lifecycleService.startServer()
                        if (result < 0) {
                            flash.message = message(code:
                         "server.action.update.changesMightNotHaveBeenApplied")
                        } else if (result == 0) {
                            flash.message = message(code:
                                "server.action.update.svnRunning")
                        } else {
                            flash.error = message(code:
                                "server.action.update.generalError")
                        }
                    } else {
                        serverConfService.writeConfigFiles()
                        flash.error = message(code:
                                "server.action.update.cantRestartServer")
                    }
                } catch (CantBindPortException cantStopRunningServer) {
                    flash.error = cantStopRunningServer.getMessage(
                        RCU.getLocale(request))
                }
            } else {
                serverConfService.writeConfigFiles()
                flash.message = message(code:"server.action.update.changesMade")
            }

            // update networkingService.selectedInterface
            networkingService.setSelectedInterface(server.netInterface)

            render(view: params.view, model : prepareServerViewModel(server))

        } else {
            flash.error = message(code:"server.action.update.invalidSettings")
            // discard entity changes and redisplay the edit screen
            server.discard()
            render(view: params.view, model : prepareServerViewModel(server))
        }
    }

  
    private Map prepareServerViewModel (Server server) {

        def networkInterfaces = networkingService
            .getNetworkInterfacesWithIPAddresses()
            .collect{ it.getName() }
        Collections.sort(networkInterfaces)

        int portValue = Integer.parseInt(server.port.toString())
        boolean isPrivatePort = portValue < 1024
        boolean isStandardPort = portValue == 80 || portValue == 443
        def showPortInstructions = isPrivatePort &&
            !lifecycleService.isDefaultPortAllowed()
        def isSolaris = operatingSystemService.isSolaris()
        def config = ConfigurationHolder.config

        return [
            server : server,
            isStarted: lifecycleService.isStarted(),
            networkInterfaces: networkInterfaces,
            ipv4Addresses: networkingService.getIPv4Addresses(),
            ipv6Addresses: networkingService.getIPv6Addresses(),
            addrInterfaceMap: networkingService
                .getInetAddressNetworkInterfaceMap(),
            csvnHome: config.svnedge.appHome ?
                config.svnedge.appHome : '<AppHome>',
            csvnConf: ConfigUtil.confDirPath(),
            privatePortInstructions: showPortInstructions,
            isStandardPort: isStandardPort,
            isSolaris: isSolaris,
            console_user: System.getProperty("user.name"),
            httpd_group: serverConfService.httpdGroup,
            isConfigurable: serverConfService.createOrValidateHttpdConf()
        ]
    }
    
    private static final String UNCHANGED = "_UnChAnGeD"
    def editMail = {
        def server = Server.getServer()
        def config = MailConfiguration.getConfiguration()
        hidePassword(config)
        return [config: config, server: server]
    }
    
    private void hidePassword(config) {
        config.discard()
        if (config.authPass) {
            config.authPass = UNCHANGED
        }
    }
     
     def updateMail = {
         def config = MailConfiguration.getConfiguration()
         if (params.enabled) {
            if (saveConfig(config)) {
                flash.message = message(code:"server.action.updateMail.success")
                redirect(action: 'editMail')
            }
            else {
                request.error = message(code:"server.action.update.invalidSettings")
                hidePassword(config)
                render(view: "editMail", model: [config: config, server: Server.getServer()])
            }
         } else {
             config.enabled = false
             mailNotificationService.saveMailConfiguration(config)
             flash.message = message(code:"server.action.updateMail.disabled")
             redirect(action: 'editMail')
         }
     } 
     
     private boolean saveConfig(config) {
         // bind data, excluding password
         bindData(config, params, ['authPass'])
         // if a new password has been input, copy to the entity
         def password = params['authPass']
         if (password != UNCHANGED) {
             config.authPass = password ?
                     securityService.encrypt(password) : ''
         }
         return mailNotificationService.saveMailConfiguration(config)
     }
     
     def testMail = {
         MailConfiguration config = MailConfiguration.getConfiguration()
         Server server = Server.getServer()
         if (saveConfig(config)) {
            def subject = message(code: 'server.action.testMail.subject')
            def body = message(code: 'server.action.testMail.body')
            def future = mailNotificationService
                    .sendTestMail(server.adminEmail, subject, body)
            try {
                def result = future.get(TEST_MAIL_WAIT_SECONDS, TimeUnit.SECONDS)
                if (result) {
                    request.error = message(result)
                } else {
                    flash.message = message(code: "server.action.testMail.success",
                                            args:[server.adminEmail])
                    redirect(action: 'editMail')
                    return
                }
            } catch (CancellationException e) {
                 request.error = message(code: "server.action.testMail.cancelled")
            } catch (TimeoutException e) {
                session[TEST_MAIL_RESULT] = future                
            }
        } else {
            request.error = message(code:"server.action.update.invalidSettings")
        }
        hidePassword(config)
        render(view: "editMail", model: [config: config, server: Server.getServer()])
    }
    
    def cancelTestMail = {
        def future = session[TEST_MAIL_RESULT]
        if (future && !future.isCancelled()) {
            future.cancel(true)
        }
        session[TEST_MAIL_RESULT] = null
        flash.message = message(code: "server.action.testMail.cancelled")
        redirect(action: 'editMail')
    }

    def testMailResult = {
        def result = TestMailResult.NOT_RUNNING
        def msg = null
        def future = session[TEST_MAIL_RESULT]
        if (future) {
            if (future.cancelled) {
                result = TestMailResult.CANCELLED
            } else if (future.done) {
                msg = future.get()
                result = msg ? TestMailResult.FAILED : TestMailResult.SUCCESS
                session[TEST_MAIL_RESULT] = null
            } else {
                result = TestMailResult.STILL_RUNNING
            }
        }
        // Prevent IE caching
        response.addHeader("Cache-Control", "max-age=0,no-cache,no-store")
        return render([result: result.toString(), errorMessage: message(msg)] as JSON)
    }

    static enum TestMailResult { CANCELLED, STILL_RUNNING, NOT_RUNNING, SUCCESS, FAILED }
}

class CtfCredentialCommand {
    String ctfUsername
    String ctfPassword
    static constraints = {
        ctfUsername(blank:false)
        ctfPassword(blank:false)
    }
}
