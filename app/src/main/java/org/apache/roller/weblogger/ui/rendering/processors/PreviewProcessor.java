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
package org.apache.roller.weblogger.ui.rendering.processors;

import org.apache.roller.weblogger.business.themes.SharedTheme;
import org.apache.roller.weblogger.business.themes.ThemeManager;
import org.apache.roller.weblogger.pojos.Template;
import org.apache.roller.weblogger.pojos.Template.ComponentType;
import org.apache.roller.weblogger.pojos.Weblog;
import org.apache.roller.weblogger.ui.rendering.Renderer;
import org.apache.roller.weblogger.ui.rendering.RendererManager;
import org.apache.roller.weblogger.ui.rendering.requests.WeblogPageRequest;
import org.apache.roller.weblogger.util.Utilities;
import org.apache.roller.weblogger.util.cache.CachedContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Responsible for rendering weblog page previews.
 * <p>
 * This servlet is used as part of the authoring interface to provide previews
 * of what a weblog will look like with a given theme.  It is not available
 * outside of the authoring interface.
 */
@RestController
@RequestMapping(path = "/tb-ui/authoring/preview/**")
public class PreviewProcessor extends AbstractProcessor {

    private static Logger log = LoggerFactory.getLogger(PreviewProcessor.class);

    @Autowired
    private RendererManager rendererManager = null;

    public void setRendererManager(RendererManager rendererManager) {
        this.rendererManager = rendererManager;
    }

    @Autowired
    protected ThemeManager themeManager;

    public void setThemeManager(ThemeManager themeManager) {
        this.themeManager = themeManager;
    }

    @PostConstruct
    public void init() {
        log.info("Initializing PreviewProcessor...");
    }

    @RequestMapping(method = RequestMethod.GET)
    public void getPreviewPage(HttpServletRequest request, HttpServletResponse response) throws IOException {
        log.debug("Entering");

        Weblog weblog;
        WeblogPageRequest previewRequest;

        previewRequest = new WeblogPageRequest(request);

        // lookup weblog specified by preview request
        weblog = previewRequest.getWeblog();
        if (weblog == null) {
            log.debug("error creating preview request: {}", previewRequest);
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // if theme provided, indicates a theme preview rather than a entry draft preview
        String previewThemeName = request.getParameter("theme");
        if (previewThemeName != null) {

            // try getting the preview theme
            log.debug("preview theme = {}", previewThemeName);

            SharedTheme previewTheme = null;

            try {
                previewTheme = themeManager.getSharedTheme(previewThemeName);
            } catch (IllegalArgumentException tnfe) {
                // unknown theme given in URL, so default to current theme below
            }

            // construct a temporary Website object for this request
            // and set the weblog theme to our previewTheme
            Weblog tmpWebsite = new Weblog();
            tmpWebsite.setData(weblog);
            if (previewTheme != null && previewTheme.isEnabled()) {
                tmpWebsite.setTheme(previewTheme.getId());
                tmpWebsite.setTempPreviewWeblog(true);
            }

            // we've got to set the weblog in our previewRequest because that's
            // the object that gets referenced during rendering operations
            previewRequest.setWeblog(tmpWebsite);
            weblog = tmpWebsite;
        }

        Template page = null;
        if ("page".equals(previewRequest.getContext())) {
            page = previewRequest.getWeblogTemplate();

            // If request specified tags section index, then look for custom template
        } else if ("tags".equals(previewRequest.getContext()) && previewRequest.getTag() == null) {
            try {
                page = themeManager.getWeblogTheme(weblog).getTemplateByAction(ComponentType.TAGSINDEX);
            } catch (Exception e) {
                log.error("Error getting weblog page for action 'tagsIndex'", e);
            }

            // if we don't have a custom tags page then 404, we don't let
            // this one fall through to the default template
            if (page == null) {
                if (!response.isCommitted()) {
                    response.reset();
                }
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            // If this is a permalink then look for a permalink template
        } else if (previewRequest.getWeblogAnchor() != null) {
            try {
                page = themeManager.getWeblogTheme(weblog).getTemplateByAction(ComponentType.PERMALINK);
            } catch (Exception e) {
                log.error("Error getting weblog page for action 'permalink'", e);
            }
        }

        if (page == null) {
            page = themeManager.getWeblogTheme(weblog).getTemplateByAction(ComponentType.WEBLOG);
        }

        // Still no page?  Then that is a 404
        if (page == null) {
            if (!response.isCommitted()) {
                response.reset();
            }
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        log.debug("preview page found, dealing with it");

        // set the content type
        String contentType = page.getRole().getContentType();

        // looks like we need to render content
        Map<String, Object> model;

        // special hack for menu tag
        request.setAttribute("pageRequest", previewRequest);

        // populate the rendering model
        Map<String, Object> initData = new HashMap<>();
        initData.put("parsedRequest", previewRequest);

        // Load models for page previewing
        model = getModelMap("previewModelSet", initData);

        // Load special models for site-wide blog
        if (themeManager.getSharedTheme(weblog.getTheme()).isSiteWide()) {
            model.putAll(getModelMap("siteModelSet", initData));
        }

        // lookup Renderer we are going to use
        Renderer renderer;
        try {
            log.debug("Looking up renderer");
            renderer = rendererManager.getRenderer(page, previewRequest.getDeviceType());
        } catch (Exception e) {
            // nobody wants to render my content :(
            log.error("Couldn't find renderer for page {}", page.getId(), e);

            if (!response.isCommitted()) {
                response.reset();
            }
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // render content
        CachedContent rendererOutput = new CachedContent(Utilities.TWENTYFOUR_KB_IN_BYTES);
        try {
            log.debug("Doing rendering");
            renderer.render(model, rendererOutput.getCachedWriter());

            // flush rendered output and close
            rendererOutput.flush();
            rendererOutput.close();
        } catch (Exception e) {
            // bummer, error during rendering
            log.error("Error during rendering for page {}", page.getId(), e);

            if (!response.isCommitted()) {
                response.reset();
            }
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // post rendering process

        // flush rendered content to response
        log.debug("Flushing response output");
        response.setContentType(contentType);
        response.setContentLength(rendererOutput.getContent().length);
        response.getOutputStream().write(rendererOutput.getContent());
        log.debug("Exiting");
    }

}
