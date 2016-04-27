/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  The ASF licenses this file to You
 * under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

import javax.persistence.Basic;
import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.io.Serializable;
import java.util.Date;


/**
 * User bean with sensitive information not available, useful as components in other objects where it is not desired
 * to be able to retrieve confidential information (username, password, etc.) from the User.
 */
@Entity
@Table(name="weblogger_user")
@NamedQueries({
        @NamedQuery(name="SafeUser.getAll",
                query="SELECT s FROM SafeUser s"),
        @NamedQuery(name="SafeUser.getByEnabled",
                query="SELECT s FROM SafeUser s WHERE s.enabled = ?1"),
        @NamedQuery(name="SafeUser.getByScreenNameOrderByScreenName",
                query="SELECT s FROM SafeUser s WHERE s.screenName= ?1 ORDER BY s.screenName"),
        @NamedQuery(name="SafeUser.getByScreenNameOrEmailAddressStartsWith",
                query="SELECT s FROM SafeUser s WHERE s.screenName LIKE ?1 OR s.emailAddress LIKE ?1"),
        @NamedQuery(name="SafeUser.getByEnabled&ScreenNameOrEmailAddressStartsWith",
                query="SELECT s FROM SafeUser s WHERE s.enabled = ?1 AND (s.screenName LIKE ?2 OR s.emailAddress LIKE ?3)")
})
public class SafeUser implements Serializable {

    public static final long serialVersionUID = -6354583200913127874L;

    private String  id;
    private String  screenName;
    private String  emailAddress;
    private Date    dateCreated;
    private String  locale;
    private String  timeZone;
    private Boolean enabled = Boolean.TRUE;

    public SafeUser() {
    }

    public SafeUser(String id,
                    String screenName,
                    String emailAddress,
                    String locale,
                    String timeZone,
                    Boolean isEnabled) {
        this.id = id;
        this.screenName = screenName;
        this.emailAddress = emailAddress;
        this.locale = locale;
        this.timeZone = timeZone;
        this.enabled = isEnabled;
    }

    @Id
    public String getId() {
        return this.id;
    }
    
    public void setId( String id ) {
        this.id = id;
    }

    @Basic(optional=false)
    public String getScreenName() {
        return this.screenName;
    }
    
    public void setScreenName( String screenName ) {
        this.screenName = screenName;
    }

    @Basic(optional=false)
    public String getEmailAddress() {
        return this.emailAddress;
    }
    
    public void setEmailAddress( String emailAddress ) {
        this.emailAddress = emailAddress;
    }

    @Basic(optional=false)
    @Temporal(TemporalType.TIMESTAMP)
    public Date getDateCreated() {
        if (dateCreated == null) {
            return null;
        } else {
            return (Date)dateCreated.clone();
        }
    }

    public void setDateCreated(final Date date) {
        if (date != null) {
            dateCreated = (Date)date.clone();
        } else {
            dateCreated = null;
        }
    }

    public String getLocale() {
        return this.locale;
    }
    
    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getTimeZone() {
        return this.timeZone;
    }
    
    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }
    
    
    /**
     * Is this user account enabled?  Disabled accounts cannot login.
     */
    @Column(name="isenabled", nullable=false)
    public Boolean getEnabled() {
        return this.enabled;
    }
    
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
    
    //------------------------------------------------------- Good citizenship
    
    public String toString() {
        String stringVal = "{" + getId();
        stringVal += ", " + getScreenName();
        stringVal += ", " + getEmailAddress();
        stringVal += ", " + getEnabled();
        stringVal += "}";
        return stringVal;
    }
    
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof SafeUser)) {
            return false;
        }
        SafeUser o = (SafeUser)other;
        return new EqualsBuilder().append(getId(), o.getId()).isEquals();
    }
    
    public int hashCode() {
        return new HashCodeBuilder().append(getId()).toHashCode();
    }

}