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
package org.apache.roller.weblogger.ui.rendering.filters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.ui.rendering.RequestMapper;

/**
 * Provides generalized request mapping capabilities.
 *
 * Incoming requests can be inspected by a series of RequestMappers and can
 * potentially be re-routed to different places within the application.
 */
public class RequestMappingFilter implements Filter {
    
    private static Log log = LogFactory.getLog(RequestMappingFilter.class);
    
    // list of RequestMappers that will be inspecting the request
    private List<RequestMapper> requestMappers = new ArrayList<>();

    public void setRequestMappers(List<RequestMapper> requestMappers) {
        this.requestMappers = requestMappers;
    }

    public void init(FilterConfig filterConfig) {}

    /**
     * Inspect incoming urls and see if they should be routed.
     */
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        
        log.debug("entering");
        // give each mapper a chance to handle the request
        for (RequestMapper mapper : requestMappers) {
            log.debug("trying mapper " + mapper.getClass().getName());

            boolean wasHandled = mapper.handleRequest(request, response);
            if(wasHandled) {
                // if mapper has handled the request then we are done
                log.debug("request handled by " + mapper.getClass().getName());
                log.debug("exiting");
                return;
            }
        }
        log.debug("request not mapped, exiting");
        // nobody handled the request, so let it continue as usual
        chain.doFilter(request, response);
    }

    public void destroy() {}
    
}
