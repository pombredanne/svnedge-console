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
package com.collabnet.svnedge.integration.command.impl

import org.apache.log4j.Logger
import com.collabnet.svnedge.domain.Repository 
import com.collabnet.svnedge.domain.Server 
import com.collabnet.svnedge.domain.integration.CtfServer 
import com.collabnet.svnedge.domain.integration.ReplicatedRepository 
import com.collabnet.svnedge.integration.command.AbstractRepositoryCommand 
import com.collabnet.svnedge.integration.command.ShortRunningCommand 

/**
 * This command uses svnsync to update the given repository
 */
public class RepoSyncCommand extends AbstractRepositoryCommand 
        implements ShortRunningCommand {

    private Logger log = Logger.getLogger(getClass())

    def constraints() {
        def repoName = getRepoName()
        log.debug("Acquiring the replica commands executor service...")
        def svn = getService("replicaCommandExecutorService")
        if (!this.params.repoName) {
            throw new IllegalArgumentException("The repo path must be provided")
        }
    }

    def execute() {
        def repoName = getRepoName()

        log.debug("Synchronizing repo: " + repoName + " for revision " +
            this.params.revision)
        def commandLineService = getService("commandLineService")
        def syncRepoURI = commandLineService.createSvnFileURI(
                new File(Server.getServer().repoParentDir, repoName))
        def ctfServer = CtfServer.getServer()
        def username = ctfServer.ctfUsername
        def securityService = getService("securityService")
        def password = securityService.decrypt(ctfServer.ctfPassword)
        def repo = Repository.findByName(repoName)
        def replRepo = ReplicatedRepository.findByRepo(repo)
        execSvnSync(replRepo, System.currentTimeMillis(), username, password, 
            syncRepoURI)
    }

    def undo() {
        log.debug("Can't undo an svnsync")
    }
}
