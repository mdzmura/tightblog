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
package org.apache.roller.weblogger.ui.rendering.velocity;

import org.apache.commons.collections.ExtendedProperties;
import org.apache.roller.weblogger.business.WebloggerContext;
import org.apache.roller.weblogger.business.themes.SharedTheme;
import org.apache.roller.weblogger.business.themes.ThemeManager;
import org.apache.roller.weblogger.pojos.Template;
import org.apache.roller.weblogger.pojos.TemplateRendition;
import org.apache.roller.weblogger.pojos.TemplateRendition.RenditionType;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.runtime.resource.Resource;
import org.apache.velocity.runtime.resource.loader.ResourceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

/**
 * The ThemeResourceLoader is a Velocity template loader which loads templates
 * that are part of themes.  Resources fitting this loader are of two formats:
 *
 * For templates from shared themes: <theme name>:<template name>[|<renditionType>]
 * For templates that are overridden or weblog-specific:
 *                                   <weblog template id>[|<renditionType>]
 * RenditionType.NORMAL is used if renditionType above is omitted.
 */
public class ThemeResourceLoader extends ResourceLoader {

    private static Logger logger = LoggerFactory.getLogger(ThemeResourceLoader.class);

    @Override
    public void init(ExtendedProperties configuration) {
        if (logger.isDebugEnabled()) {
            logger.debug(configuration.toString());
        }
    }

    /**
     * Get an InputStream so that the Runtime can build a template with it.
     *
     * @param resourceId resource identifier for template rendition
     * @return InputStream containing template
     * @throws ResourceNotFoundException
     */
    @Override
    public InputStream getResourceStream(String resourceId) {

        logger.debug("Looking for: {}", resourceId);

        if (resourceId == null || resourceId.length() < 1) {
            throw new ResourceNotFoundException("Need to specify a template name!");
        }

        Template template;
        RenditionType renditionType = RenditionType.NORMAL;

        if (resourceId.contains("|")) {
            String[] pair = resourceId.split("\\|");
            resourceId = pair[0];
            renditionType = RenditionType.valueOf(pair[1].toUpperCase());
        }

        // by here, resourceId will lack the "|renditionType"
        if (resourceId.contains(":")) {
            // shared theme, stored in a file
            String[] sharedThemeParts = resourceId.split(":", 2);
            if (sharedThemeParts.length != 2) {
                throw new ResourceNotFoundException("Invalid Theme resource key " + resourceId);
            }
            ThemeManager themeMgr = WebloggerContext.getWeblogger().getThemeManager();
            SharedTheme theme = themeMgr.getSharedTheme(sharedThemeParts[0]);
            template = theme.getTemplateByName(sharedThemeParts[1]);
        } else {
            // weblog-only theme in database
            template = WebloggerContext.getWeblogger().getWeblogManager().getTemplate(resourceId);
        }

        if (template == null) {
            throw new ResourceNotFoundException("Template \"" + resourceId + "\" not found");
        }

        final String contents;
        TemplateRendition templateCode = template.getTemplateRendition(renditionType);
        if (templateCode == null && renditionType != RenditionType.NORMAL) {
            // fall back to standard rendition if mobile or other unavailable
            templateCode = template.getTemplateRendition(RenditionType.NORMAL);
        }
        if (templateCode != null) {
            contents = templateCode.getRendition();
        } else {
            throw new ResourceNotFoundException("Rendering [" + renditionType.name() +
                    "] of Template [" + resourceId + "] not found.");
        }

        logger.debug("Resource found!");

        // return the input stream
        try {
            return new ByteArrayInputStream(contents.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException uee) {
            // should never happen, UTF-8 required to be supported by a JVM
            String msg = "Rendering problem trying to load resource " + resourceId;
            logger.error(msg, uee);
            throw new ResourceNotFoundException(msg);
        }
    }

    /**
     * Velocity reloading not used.  Instead, template changes clear the page cache, triggering page regeneration that way.
     */
    @Override
    public boolean isSourceModified(Resource resource) {
        return false;
    }

    /**
     * Unused.
     */
    @Override
    public long getLastModified(Resource resource) {
        return 0;
    }

}
