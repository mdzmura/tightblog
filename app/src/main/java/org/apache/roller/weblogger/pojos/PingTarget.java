/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  The ASF licenses this file to You
 * under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.  For additional information regarding
 * copyright in this work, please see the NOTICE file in the top level
 * directory of this distribution.
 *
 * Source file modified from the original ASF source; all changes made
 * are also under Apache License.
 */
package org.apache.roller.weblogger.pojos;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.roller.weblogger.util.Utilities;

import javax.persistence.Basic;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import java.time.Instant;

/**
 * Ping target.   Each instance represents a possible target of a weblog update ping that we send.
 *
 * @author <a href="mailto:anil@busybuddha.org">Anil Gangolli</a>
 */
@Entity
@Table(name = "ping_target")
@NamedQueries({
        @NamedQuery(name = "PingTarget.getPingTargetsOrderByName",
                query = "SELECT p FROM PingTarget p ORDER BY p.name"),
        @NamedQuery(name = "PingTarget.getEnabledPingTargets",
                query = "SELECT p FROM PingTarget p where p.enabled = true")
})
public class PingTarget {

    private String id;
    private String name;
    private String pingUrl;
    private Instant lastSuccess;
    private boolean enabled;

    public PingTarget() {
    }

    /**
     * Constructor.
     *
     * @param name       the descriptive name of this target
     * @param pingUrl    the URL to which to send the ping
     * @param autoEnable if true, pings sent to target by default
     */
    public PingTarget(String name, String pingUrl, boolean autoEnable) {
        this.id = Utilities.generateUUID();
        this.name = name;
        this.pingUrl = pingUrl;
        this.lastSuccess = null;
        this.enabled = autoEnable;
    }

    @Id
    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * Name is given by the administrator, it is descriptive and
     * not necessarily unique.
     *
     * @return the name of this ping target
     */

    @Basic(optional = false)
    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Get the URL to ping.
     *
     * @return the URL to ping.
     */
    @Basic(optional = false)
    public String getPingUrl() {
        return pingUrl;
    }

    public void setPingUrl(String pingUrl) {
        this.pingUrl = pingUrl;
    }

    /**
     * Get the timestamp of the last successful ping (UTC/GMT).
     *
     * @return the timestamp of the last successful ping; <code>null</code> if the target has not yet been used.
     */
    public Instant getLastSuccess() {
        return lastSuccess;
    }

    public void setLastSuccess(Instant lastSuccess) {
        this.lastSuccess = lastSuccess;
    }

    /**
     * Is this ping target enabled (i.e., will send pings out on blog updates)?
     *
     * @return true if ping target is enabled, false otherwise.
     */
    @Basic(optional = false)
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    //------------------------------------------------------- Good citizenship

    public String toString() {
        String buf = "{" + getId();
        buf += ", " + getName();
        buf += ", " + getPingUrl();
        buf += ", " + getLastSuccess();
        buf += ", " + isEnabled() + "}";
        return buf;
    }

    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof PingTarget)) {
            return false;
        }
        PingTarget o = (PingTarget) other;
        return new EqualsBuilder()
                .append(getId(), o.getId())
                .isEquals();
    }

    public int hashCode() {
        return new HashCodeBuilder()
                .append(getId())
                .toHashCode();
    }

}
