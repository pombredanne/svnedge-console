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
package com.collabnet.svnedge.domain

/**
 * Data on how to send mail. 
 * We expect there to be only one config object defined.
 */
class MailConfiguration {

    boolean enabled = false
    String serverName = "localhost"
    int port = 25
    String authUser
    String authPass
    MailSecurityMethod securityMethod = MailSecurityMethod.NONE
    MailAuthMethod authMethod = MailAuthMethod.NONE
    String fromAddress
    
    String createFromAddress() {
        String addr = fromAddress
        if (!addr) {
            String host = serverName
            if (host.startsWith('smtp.')) {
                host = host.substring(5)
            } else if (host.startsWith('exchange.')) {
                host = host.substring(9)
            }
            addr = (authUsername ?: 'SubversionEdge') + '@' + host
        }
        return addr
    }
    
    static constraints = {
        serverName(validator: isBlank, unique: true)
        port(validator: isBlank)
        authUser(nullable:true)
        authPass(nullable: true)
        authMethod(nullable:true)
        securityMethod(nullable:false)
        fromAddress(nullable:true, email: true)
    }
    
    private static def isBlank = { val, obj -> 
            return (val || !obj.enabled) ? null : ['blank']
    }
    
    static MailConfiguration getConfiguration() {
        return MailConfiguration.get(1) ?: new MailConfiguration()
    }
}

enum MailAuthMethod { NONE, PLAINTEXT, ENCRYPTED, NTLM, KERBEROS }
enum MailSecurityMethod { NONE, STARTTLS, SSL }

