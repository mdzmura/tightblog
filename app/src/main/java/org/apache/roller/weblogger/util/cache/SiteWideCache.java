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
package org.apache.roller.weblogger.util.cache;

import org.apache.roller.weblogger.business.themes.ThemeManager;
import org.apache.roller.weblogger.pojos.WeblogBookmark;
import org.apache.roller.weblogger.pojos.WeblogEntryComment;
import org.apache.roller.weblogger.pojos.User;
import org.apache.roller.weblogger.pojos.WeblogCategory;
import org.apache.roller.weblogger.pojos.WeblogEntry;
import org.apache.roller.weblogger.pojos.WeblogTemplate;
import org.apache.roller.weblogger.pojos.Weblog;

import javax.annotation.PostConstruct;
import java.time.Instant;

/**
 * Cache for site-wide weblog content.  The site weblog needs a different type of
 * cache from regular weblogs because only certain changes from a regular weblog
 * (for example, new weblog entry) need to trigger a refresh to the site weblog,
 * while other changes from regular weblogs do not alter the appearance of the site
 * weblog and hence the latter's pages can remain as-is in the cache.
 */
public class SiteWideCache extends ExpiringCache implements BlogEventListener {

    // keep a cached version of cache last refresh time for 304 Not Modified calculations
    private ExpiringCacheEntry lastUpdateTime = null;

    private ThemeManager themeManager;

    public void setThemeManager(ThemeManager themeManager) {
        this.themeManager = themeManager;
    }

    @PostConstruct
    public void init() {
        super.init();
        cacheManager.registerHandler(this);
    }

    public void clear() {
        if (enabled) {
            super.clear();
            this.lastUpdateTime = null;
        }
    }

    public Instant getLastModified() {
        Instant lastModified = null;

        // first try our cached version
        if (this.lastUpdateTime != null) {
            lastModified = (Instant) this.lastUpdateTime.getValue();
        }

        // still null, we need to get a fresh value
        if (lastModified == null) {
            lastModified = Instant.now();
            this.lastUpdateTime = new ExpiringCacheEntry(lastModified, timeoutInMS);
        }

        return lastModified;
    }

    /**
     * A weblog entry has changed.
     */
    public void invalidate(WeblogEntry entry) {
        if (enabled) {
            this.contentCache.clear();
            this.lastUpdateTime = null;
        }
    }

    /**
     * A weblog has changed.
     */
    public void invalidate(Weblog website) {
        if (enabled) {
            this.contentCache.clear();
            this.lastUpdateTime = null;
        }
    }

    /**
     * A bookmark has changed, invalidate only if site blog itself changed.
     */
    public void invalidate(WeblogBookmark bookmark) {
        if (enabled && themeManager.getSharedTheme(bookmark.getWeblog().getTheme()).isSiteWide()) {
            invalidate(bookmark.getWeblog());
        }
    }

    /**
     * A comment has changed, invalidate only if site blog itself changed.
     */
    public void invalidate(WeblogEntryComment comment) {
        if (enabled && themeManager.getSharedTheme(comment.getWeblogEntry().getWeblog().getTheme()).isSiteWide()) {
            invalidate(comment.getWeblogEntry().getWeblog());
        }
    }

    /**
     * A user profile has changed.
     */
    public void invalidate(User user) {
        // ignored
    }

    /**
     * A category has changed, invalidate only if site blog itself changed.
     */
    public void invalidate(WeblogCategory category) {
        if (enabled && themeManager.getSharedTheme(category.getWeblog().getTheme()).isSiteWide()) {
            invalidate(category.getWeblog());
        }
    }

    /**
     * A weblog template has changed, invalidate only if site blog itself changed.
     */
    public void invalidate(WeblogTemplate template) {
        if (enabled && themeManager.getSharedTheme(template.getWeblog().getTheme()).isSiteWide()) {
            invalidate(template.getWeblog());
        }
    }
}
