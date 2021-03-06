/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  The ASF licenses this file to You
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
package org.apache.roller.weblogger.ui.rendering.requests;

import java.util.Locale;
import javax.servlet.http.HttpServletRequest;

import org.apache.roller.weblogger.business.WebloggerContext;
import org.apache.roller.weblogger.pojos.Weblog;
import org.apache.roller.weblogger.util.Utilities;
import org.springframework.mobile.device.DeviceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a request to a weblog.
 * <p>
 * This is a fairly generic parsed request which is only trying to figure out
 * the elements of a weblog request which apply to all weblogs.  We try to
 * determine the weblogHandle, and then what extra path info remains.  The basic
 * format is like this ...
 * <p>
 * /<weblogHandle>[/extra/path/info]
 * <p>
 * All weblog urls require a weblogHandle, so we ensure that part of the url is
 * properly specified, and path info is always optional.
 * <p>
 * NOTE: this class purposely exposes a getPathInfo() method which provides the
 * path info specified by the request that has not been parsed by this
 * particular class.  this makes it relatively easy for subclasses to extend
 * this class and simply pick up where it left off in the parsing process.
 * <p>
 * NOTE: It is extremely important to mention that this class and all of its
 * subclasses are meant to be extremely light weight.  Meaning they should
 * avoid time consuming operations whenever possible, especially operations
 * which require a trip to the db.
 */
public class WeblogRequest {

    private static Logger log = LoggerFactory.getLogger(WeblogRequest.class);

    // lightweight attributes
    private String weblogHandle = null;
    private String pathInfo = null;
    private String authenticatedUser = null;
    private DeviceType deviceType = DeviceType.NORMAL;

    // heavyweight attributes
    private Weblog weblog = null;
    private Locale localeInstance = null;

    public WeblogRequest() {
    }

    public WeblogRequest(HttpServletRequest request) {

        // login status
        java.security.Principal principal = request.getUserPrincipal();
        if (principal != null) {
            this.authenticatedUser = principal.getName();
        }
        // set the detected type of the request
        deviceType = Utilities.getDeviceType(request);

        String path = request.getPathInfo();

        log.debug("parsing path {}", path);

        // first, cleanup extra slashes and extract the weblog weblogHandle
        if (path != null && path.trim().length() > 1) {

            // strip off the leading slash
            path = path.substring(1);

            // strip off trailing slash if needed
            if (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }

            String[] pathElements = path.split("/", 2);
            if (pathElements[0].trim().length() > 0) {
                this.weblogHandle = pathElements[0];
            } else {
                // no weblogHandle in path info
                throw new IllegalArgumentException("Not a weblog request, " + request.getRequestURL());
            }

            // if there is more left of the path info then hold onto it
            if (pathElements.length == 2) {
                path = pathElements[1];
            } else {
                path = null;
            }
        }

        if (path != null && path.trim().length() > 0) {
            this.pathInfo = path;
        }

        log.debug("handle = {}, pathInfo = {}", weblogHandle, pathInfo);
    }

    public String getWeblogHandle() {
        return weblogHandle;
    }

    public void setWeblogHandle(String weblogHandle) {
        this.weblogHandle = weblogHandle;
    }

    public String getPathInfo() {
        return pathInfo;
    }

    public Weblog getWeblog() {
        if (weblog == null && weblogHandle != null) {
            weblog = WebloggerContext.getWeblogger().getWeblogManager().getWeblogByHandle(weblogHandle, true);
            if (weblog == null) {
                throw new IllegalStateException("Unknown Weblog: " + weblogHandle);
            }
        }
        return weblog;
    }

    public void setWeblog(Weblog weblog) {
        this.weblog = weblog;
    }

    /**
     * Get the Locale instance to be used for this request.
     * <p>
     * The Locale is determined via these rules ...
     * 1. if a locale is explicitly specified, then it is used
     * 2. if no locale is specified, then use the weblog default locale
     */
    public Locale getLocaleInstance() {
        if (localeInstance == null) {
            localeInstance = getWeblog().getLocaleInstance();
        }
        return localeInstance;
    }

    public String getAuthenticatedUser() {
        return this.authenticatedUser;
    }

    public boolean isLoggedIn() {
        return (this.authenticatedUser != null);
    }

    public DeviceType getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(DeviceType type) {
        this.deviceType = type;
    }

}
