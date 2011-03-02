/*
 * CollabNet Subversion Edge
 * Copyright (C) 2011, CollabNet Inc. All rights reserved.
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
package com.collabnet.svnedge.replication.command.event

/**
 * This event signals the delivery service that the connectivity with the
 * replica manager has been restored. That can be used to deliver any pending
 * results.
 *
 * @author Marcello de Sales (mdesales@collab.net)
 *
 */
final class ConnectivityWithReplicaManagerRestoredEvent 
        extends ReplicaCommandsExecutionEvent {

    def ConnectivityWithReplicaManagerRestoredEvent(source, context) {
        super(source, context)
    }
}