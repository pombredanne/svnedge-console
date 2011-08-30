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
package com.collabnet.svnedge.integration

/**
 * Any problem related to cloud services
 */
class CloudServicesException extends Exception {
    /**
     * The key to the messages.properties used.
     */
    def messageKey

    /**
     * Creates a new exception with the given hostname, error message and
     * the cause.
     * @param key is the messages key in the i18n messages.
     * @param cause is the original cause.
     */
    public CloudServicesException(String key, Throwable cause) {
        super(key, cause)
        this.messageKey = key
    }

    /**
     * Creates a new exception with the given error message.
     * @param key is the messages key in the i18n messages.
     */
    public CloudServicesException(String key) {
        super(key)
        this.messageKey = key
    }
}
