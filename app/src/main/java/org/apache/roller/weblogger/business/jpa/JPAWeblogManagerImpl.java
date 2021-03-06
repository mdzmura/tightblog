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
package org.apache.roller.weblogger.business.jpa;

import org.apache.commons.lang3.StringUtils;
import org.apache.roller.weblogger.business.MediaFileManager;
import org.apache.roller.weblogger.business.UserManager;
import org.apache.roller.weblogger.business.WeblogEntryManager;
import org.apache.roller.weblogger.business.WeblogManager;
import org.apache.roller.weblogger.business.WebloggerStaticConfig;
import org.apache.roller.weblogger.business.search.IndexManager;
import org.apache.roller.weblogger.business.themes.ThemeManager;
import org.apache.roller.weblogger.pojos.Template.ComponentType;
import org.apache.roller.weblogger.pojos.User;
import org.apache.roller.weblogger.pojos.UserStatus;
import org.apache.roller.weblogger.pojos.UserWeblogRole;
import org.apache.roller.weblogger.pojos.Weblog;
import org.apache.roller.weblogger.pojos.WeblogBookmark;
import org.apache.roller.weblogger.pojos.WeblogCategory;
import org.apache.roller.weblogger.pojos.WeblogEntry;
import org.apache.roller.weblogger.pojos.WeblogEntrySearchCriteria;
import org.apache.roller.weblogger.pojos.WeblogEntryTag;
import org.apache.roller.weblogger.pojos.WeblogEntryTagAggregate;
import org.apache.roller.weblogger.pojos.WeblogRole;
import org.apache.roller.weblogger.pojos.WeblogTemplate;
import org.apache.roller.weblogger.pojos.WeblogTemplateRendition;
import org.apache.roller.weblogger.pojos.WebloggerProperties;
import org.apache.roller.weblogger.util.Blacklist;
import org.apache.roller.weblogger.util.cache.CacheManager;
import org.apache.roller.weblogger.util.cache.LazyExpiringCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.NoResultException;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class JPAWeblogManagerImpl implements WeblogManager {

    private static Logger log = LoggerFactory.getLogger(JPAWeblogManagerImpl.class);

    private UserManager userManager;
    private LazyExpiringCache weblogBlacklistCache = null;
    private WeblogEntryManager weblogEntryManager;
    private IndexManager indexManager;
    private ThemeManager themeManager;
    private final MediaFileManager mediaFileManager;
    private final JPAPersistenceStrategy strategy;
    private final CacheManager cacheManager;
    private Blacklist siteBlacklist = null;

    // Map of each weblog and its extra hit count that has had additional accesses since the
    // last scheduled updateHitCounters() call.
    private Map<String, Long> hitsTally = Collections.synchronizedMap(new HashMap<>());

    public void setWeblogBlacklistCache(LazyExpiringCache weblogBlacklistCache) {
        this.weblogBlacklistCache = weblogBlacklistCache;
    }

    public void setUserManager(UserManager userManager) {
        this.userManager = userManager;
    }

    public void setWeblogEntryManager(WeblogEntryManager weblogEntryManager) {
        this.weblogEntryManager = weblogEntryManager;
    }

    public void setIndexManager(IndexManager indexManager) {
        this.indexManager = indexManager;
    }

    public void setThemeManager(ThemeManager themeManager) {
        this.themeManager = themeManager;
    }

    // cached mapping of weblogHandles -> weblogIds
    private Map<String, String> weblogHandleToIdMap = new Hashtable<>();

    protected JPAWeblogManagerImpl(MediaFileManager mfm, JPAPersistenceStrategy strat,
                                   CacheManager cacheManager) {
        log.debug("Instantiating JPA Weblog Manager");
        this.mediaFileManager = mfm;
        this.strategy = strat;
        this.cacheManager = cacheManager;
    }

    @Override
    public void saveWeblog(Weblog weblog) {
        weblog.setLastModified(Instant.now());
        strategy.merge(weblog);
        if (themeManager.getSharedTheme(weblog.getTheme()).isSiteWide()) {
            cacheManager.invalidate(weblog);
        }
    }

    @Override
    public void removeWeblog(Weblog weblog) {
        // remove contents first, then remove weblog
        this.removeWeblogContents(weblog);
        this.strategy.remove(weblog);
        if (themeManager.getSharedTheme(weblog.getTheme()).isSiteWide()) {
            cacheManager.invalidate(weblog);
        }
        this.strategy.flush();

        // remove entry from cache mapping
        this.weblogHandleToIdMap.remove(weblog.getHandle());
    }

    /**
     * convenience method for removing contents of a weblog.
     */
    private void removeWeblogContents(Weblog weblog) {

        // remove tags
        TypedQuery<WeblogEntryTag> tagQuery = strategy.getNamedQuery("WeblogEntryTag.getByWeblog",
                WeblogEntryTag.class);
        tagQuery.setParameter(1, weblog);
        List<WeblogEntryTag> results = tagQuery.getResultList();

        for (WeblogEntryTag tagData : results) {
            if (tagData.getWeblogEntry() != null) {
                tagData.getWeblogEntry().getTags().remove(tagData);
            }
            this.strategy.remove(tagData);
        }

        // remove associated templates
        TypedQuery<WeblogTemplate> templateQuery = strategy.getNamedQuery("WeblogTemplate.getByWeblog",
                WeblogTemplate.class);
        templateQuery.setParameter(1, weblog);
        List<WeblogTemplate> templates = templateQuery.getResultList();

        for (WeblogTemplate template : templates) {
            this.strategy.remove(template);
        }

        // remove bookmarks
        TypedQuery<WeblogBookmark> bookmarkQuery = strategy.getNamedQuery("Bookmark.getByWeblog",
                WeblogBookmark.class);
        bookmarkQuery.setParameter(1, weblog);
        List<WeblogBookmark> bookmarks = bookmarkQuery.getResultList();
        for (WeblogBookmark bookmark : bookmarks) {
            this.strategy.remove(bookmark);
        }

        // remove mediafile metadata
        // remove uploaded files
        mediaFileManager.removeAllFiles(weblog);
        this.strategy.flush();

        // remove entries
        TypedQuery<WeblogEntry> refQuery = strategy.getNamedQuery("WeblogEntry.getByWeblog", WeblogEntry.class);
        refQuery.setParameter(1, weblog);
        List<WeblogEntry> entries = refQuery.getResultList();
        for (WeblogEntry entry : entries) {
            weblogEntryManager.removeWeblogEntry(entry);
        }
        this.strategy.flush();

        // delete all weblog categories
        Query removeCategories = strategy.getNamedUpdate("WeblogCategory.removeByWeblog");
        removeCategories.setParameter(1, weblog);
        removeCategories.executeUpdate();

        // remove permissions
        for (UserWeblogRole role : userManager.getWeblogRolesIncludingPending(weblog)) {
            userManager.revokeWeblogRole(role);
        }

        // remove indexing
        indexManager.removeWeblogIndexOperation(weblog);

        // check if main blog, disconnect if it is
        WebloggerProperties props = strategy.getWebloggerProperties();
        Weblog test = props.getMainBlog();
        if (test != null && test.getId().equals(weblog.getId())) {
            props.setMainBlog(null);
            strategy.store(props);
        }

        // flush the changes before returning. This is required as there is a
        // circular dependency between WeblogCategory and Weblog
        this.strategy.flush();
    }

    @Override
    public void saveTemplate(WeblogTemplate template) {
        this.strategy.store(template);

        // update weblog last modified date.  date updated by saveWeblog()
        saveWeblog(template.getWeblog());
    }

    @Override
    public void saveTemplateRendition(WeblogTemplateRendition rendition) {
        this.strategy.store(rendition);

        // update weblog last modified date.  date updated by saveWeblog()
        saveWeblog(rendition.getWeblogTemplate().getWeblog());
    }

    @Override
    public void removeTemplate(WeblogTemplate template) {
        this.strategy.remove(template);
        // update weblog last modified date.  date updated by saveWeblog()
        saveWeblog(template.getWeblog());
        if (themeManager.getSharedTheme(template.getWeblog().getTheme()).isSiteWide()) {
            cacheManager.invalidate(template.getWeblog());
        }
    }

    @Override
    public void addWeblog(Weblog newWeblog) {
        this.strategy.store(newWeblog);
        this.strategy.flush();
        this.addWeblogContents(newWeblog);
    }

    private void addWeblogContents(Weblog newWeblog) {

        if (getWeblogCount() == 1) {
            // first weblog, let's make it the frontpage one.
            WebloggerProperties props = strategy.getWebloggerProperties();
            props.setMainBlog(newWeblog);
            this.strategy.store(props);
        }

        // grant weblog creator OWNER permission
        userManager.grantWeblogRole(newWeblog.getCreator(), newWeblog, WeblogRole.OWNER, false);

        String cats = WebloggerStaticConfig.getProperty("newuser.categories");
        WeblogCategory firstCat = null;
        if (cats != null) {
            String[] splitcats = cats.split(",");
            for (String split : splitcats) {
                if (split.trim().length() == 0) {
                    continue;
                }
                WeblogCategory c = new WeblogCategory(newWeblog, split);
                if (firstCat == null) {
                    firstCat = c;
                }
                newWeblog.addCategory(c);
                this.strategy.store(c);
            }
        }

        this.strategy.store(newWeblog);

        // add default bookmarks
        String blogroll = WebloggerStaticConfig.getProperty("newuser.blogroll");
        if (blogroll != null) {
            String[] splitroll = blogroll.split(",");
            for (String splitItem : splitroll) {
                String[] rollitems = splitItem.split("\\|");
                if (rollitems.length > 1) {
                    WeblogBookmark b = new WeblogBookmark(
                            newWeblog,
                            rollitems[0],
                            rollitems[1].trim(), ""
                    );
                    newWeblog.addBookmark(b);
                    this.strategy.store(b);
                }
            }
        }

        // create initial media file directory named "default"
        mediaFileManager.createMediaDirectory(newWeblog, "default");

        // flush so that all data up to this point can be available in db
        this.strategy.flush();

    }

    @Override
    public Weblog getWeblog(String id) {
        return this.strategy.load(Weblog.class, id);
    }

    @Override
    public Weblog getWeblogByHandle(String handle) {
        return getWeblogByHandle(handle, Boolean.TRUE);
    }

    @Override
    public Weblog getWeblogByHandle(String handle, Boolean visible) {

        if (handle == null) {
            throw new IllegalArgumentException("Handle cannot be null");
        }

        // check cache first
        // NOTE: if we ever allow changing handles then this needs updating
        if (weblogHandleToIdMap.containsKey(handle)) {
            Weblog weblog = getWeblog(weblogHandleToIdMap.get(handle));

            if (weblog != null) {
                // only return weblog if enabled status matches
                if (visible == null || visible.equals(weblog.getVisible())) {
                    log.debug("weblogHandleToId CACHE HIT - {}", handle);
                    return weblog;
                }
            } else {
                // id no longer maps to an existing weblog, remove it from cache
                weblogHandleToIdMap.remove(handle);
            }
        }

        TypedQuery<Weblog> query = strategy.getNamedQuery("Weblog.getByHandle", Weblog.class);
        query.setParameter(1, handle);
        Weblog weblog;
        try {
            weblog = query.getSingleResult();
        } catch (NoResultException e) {
            weblog = null;
        }

        // add mapping to cache
        if (weblog != null) {
            log.debug("weblogHandleToId CACHE MISS - {}", handle);
            weblogHandleToIdMap.put(weblog.getHandle(), weblog.getId());
        }

        if (weblog != null && (visible == null || visible.equals(weblog.getVisible()))) {
            return weblog;
        } else {
            return null;
        }
    }

    @Override
    public List<Weblog> getWeblogs(Boolean visible, int offset, int length) {

        List<Object> params = new ArrayList<>();
        int size = 0;
        String queryString;
        StringBuilder whereClause = new StringBuilder();

        queryString = "SELECT w FROM Weblog w WHERE 1=1 ";

        if (visible != null) {
            whereClause.append(" AND ");
            params.add(size++, visible);
            whereClause.append(" w.visible = ?").append(size);
        }

        whereClause.append(" ORDER BY w.dateCreated DESC");

        TypedQuery<Weblog> query = strategy.getDynamicQuery(queryString + whereClause.toString(), Weblog.class);
        if (offset != 0) {
            query.setFirstResult(offset);
        }
        if (length != -1) {
            query.setMaxResults(length);
        }
        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }

        return query.getResultList();
    }

    @Override
    public List<User> getWeblogUsers(Weblog weblog, boolean enabledOnly) {
        List<User> users = new ArrayList<>();
        List<UserWeblogRole> roles = userManager.getWeblogRoles(weblog);
        for (UserWeblogRole role : roles) {
            User user = role.getUser();
            if (user == null) {
                log.error("ERROR user is null, userId: {}", role.getUser().getId());
                continue;
            }
            if (!enabledOnly || UserStatus.ENABLED.equals(user.getStatus())) {
                users.add(user);
            }
        }
        return users;
    }

    @Override
    public WeblogTemplate getTemplate(String id) {
        if (id == null) {
            return null;
        }
        return this.strategy.load(WeblogTemplate.class, id);
    }

    /**
     * Using JPA directly because Weblogger's Query API does too much memory allocation.
     */
    @Override
    public WeblogTemplate getTemplateByPath(Weblog weblog, String path) {

        if (weblog == null) {
            throw new IllegalArgumentException("userName is null");
        }

        if (path == null) {
            throw new IllegalArgumentException("path is null");
        }

        TypedQuery<WeblogTemplate> query = strategy.getNamedQuery("WeblogTemplate.getByWeblog&RelativePath",
                WeblogTemplate.class);
        query.setParameter(1, weblog);
        query.setParameter(2, path);
        try {
            return query.getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    @Override
    public WeblogTemplate getTemplateByAction(Weblog weblog, ComponentType action) {

        if (weblog == null) {
            throw new IllegalArgumentException("weblog is null");
        }

        if (action == null) {
            throw new IllegalArgumentException("Action name is null");
        }

        TypedQuery<WeblogTemplate> query = strategy.getNamedQuery("WeblogTemplate.getByRole",
                WeblogTemplate.class);
        query.setParameter(1, weblog);
        query.setParameter(2, action);
        try {
            return query.getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    @Override
    public WeblogTemplate getTemplateByName(Weblog weblog, String templateName) {

        if (weblog == null) {
            throw new IllegalArgumentException("weblog is null");
        }

        if (templateName == null) {
            throw new IllegalArgumentException("Template name is null");
        }

        TypedQuery<WeblogTemplate> query = strategy.getNamedQuery("WeblogTemplate.getByWeblog&Name",
                WeblogTemplate.class);
        query.setParameter(1, weblog);
        query.setParameter(2, templateName);
        try {
            return query.getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    @Override
    public List<WeblogTemplate> getTemplates(Weblog weblog) {
        if (weblog == null) {
            throw new IllegalArgumentException("weblog is null");
        }
        TypedQuery<WeblogTemplate> q = strategy.getNamedQuery(
                "WeblogTemplate.getByWeblogOrderByName", WeblogTemplate.class);
        q.setParameter(1, weblog);
        return q.getResultList();
    }

    @Override
    public Map<String, Long> getWeblogHandleLetterMap() {
        String lc = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        Map<String, Long> results = new TreeMap<>();
        TypedQuery<Long> query = strategy.getNamedQuery(
                "Weblog.getCountByHandleLike", Long.class);
        for (int i = 0; i < 26; i++) {
            char currentChar = lc.charAt(i);
            query.setParameter(1, currentChar + "%");
            List row = query.getResultList();
            Long count = (Long) row.get(0);
            results.put(String.valueOf(currentChar), count);
        }
        return results;
    }

    @Override
    public List<Weblog> getWeblogsByLetter(char letter, int offset, int length) {
        TypedQuery<Weblog> query = strategy.getNamedQuery(
                "Weblog.getByLetterOrderByHandle", Weblog.class);
        query.setParameter(1, letter + "%");
        if (offset != 0) {
            query.setFirstResult(offset);
        }
        if (length != -1) {
            query.setMaxResults(length);
        }
        return query.getResultList();
    }

    @Override
    public long getWeblogCount() {
        List<Long> results = strategy.getNamedQuery(
                "Weblog.getCountAllDistinct", Long.class).getResultList();
        return results.get(0);
    }

    @Override
    public int getHitCount(Weblog weblog) {
        Weblog copy = getWeblog(weblog.getId());
        return copy.getHitsToday();
    }

    @Override
    public List<Weblog> getHotWeblogs(int sinceDays, int offset, int length) {

        TypedQuery<Weblog> query = strategy.getNamedQuery(
                "Weblog.getByWeblog&DailyHitsGreaterThenZero&WeblogLastModifiedGreaterOrderByDailyHitsDesc", Weblog.class);
        query.setParameter(1, Instant.now().minus(sinceDays, ChronoUnit.DAYS));
        if (offset != 0) {
            query.setFirstResult(offset);
        }
        if (length != -1) {
            query.setMaxResults(length);
        }
        return query.getResultList();
    }

    @Override
    public void resetAllHitCounts() {
        log.info("daily hit counts getting reset...");
        Query q = strategy.getNamedUpdate("Weblog.updateDailyHitCountZero");
        q.executeUpdate();
        strategy.flush();
        log.info("finished resetting hit count");
    }

    @Override
    public void saveBookmark(WeblogBookmark bookmark) {
        bookmark.getWeblog().invalidateCache();
        this.strategy.store(bookmark);
    }

    @Override
    public WeblogBookmark getBookmark(String id) {
        return strategy.load(WeblogBookmark.class, id);
    }

    @Override
    public void removeBookmark(WeblogBookmark bookmark) {
        Weblog weblog = bookmark.getWeblog();
        weblog.getBookmarks().remove(bookmark);
        weblog.invalidateCache();
        this.strategy.remove(bookmark);

        if (themeManager.getSharedTheme(weblog.getTheme()).isSiteWide()) {
            cacheManager.invalidate(weblog);
        }
    }

    @Override
    public void promoteScheduledEntries() {
        log.debug("promoting scheduled entries...");

        try {
            Instant now = Instant.now();
            log.debug("looking up scheduled entries older than {}", now);

            // get all published entries older than current time
            WeblogEntrySearchCriteria wesc = new WeblogEntrySearchCriteria();
            wesc.setEndDate(now);
            wesc.setStatus(WeblogEntry.PubStatus.SCHEDULED);
            List<WeblogEntry> scheduledEntries = weblogEntryManager.getWeblogEntries(wesc);
            log.debug("promoting {} entries to PUBLISHED state", scheduledEntries.size());

            for (WeblogEntry entry : scheduledEntries) {
                entry.setStatus(WeblogEntry.PubStatus.PUBLISHED);
                weblogEntryManager.saveWeblogEntry(entry);
            }

            // commit the changes
            strategy.flush();

            // take a second pass to trigger reindexing and cache invalidations
            // this is because we need the updated entries flushed first
            for (WeblogEntry entry : scheduledEntries) {
                // trigger a cache invalidation
                cacheManager.invalidate(entry);
                // trigger search index on entry
                indexManager.addEntryReIndexOperation(entry);
            }

        } catch (Exception e) {
            log.error("Unexpected exception running task", e);
        }
        log.debug("finished promoting entries");
    }

    @Override
    public void incrementHitCount(Weblog weblog) {
        if (weblog != null) {
            Long count = hitsTally.get(weblog.getId());
            if (count == null) {
                count = 1L;
            } else {
                count = count + 1;
            }
            hitsTally.put(weblog.getId(), count);
        }
    }

    @Override
    public void updateHitCounters() {
        log.debug("updating blog hit counters...");

        // Make a reference to the current queue
        Map<String, Long> hitsTallyCopy = hitsTally;

        // reset queue for next execution
        hitsTally = Collections.synchronizedMap(new HashMap<>());

        // iterate over the tallied hits and store them in the db
        long totalHitsProcessed = 0;
        Weblog weblog;
        for (Map.Entry<String, Long> entry : hitsTallyCopy.entrySet()) {
            weblog = getWeblog(entry.getKey());
            updateHitCount(weblog, entry.getValue().intValue());
            totalHitsProcessed += entry.getValue();
        }

        // flush the results to the db
        strategy.flush();

        log.debug("Added {} hits to {} blogs", totalHitsProcessed, hitsTallyCopy.size());
    }

    private void updateHitCount(Weblog weblog, int amount) {
        weblog.setHitsToday(getHitCount(weblog) + amount);
        strategy.store(weblog);
    }

    @Override
    public void saveWeblogCategory(WeblogCategory cat) {
        WeblogCategory test = getWeblogCategoryByName(cat.getWeblog(), cat.getName());

        if (test != null && !test.getId().equals(cat.getId())) {
            throw new IllegalArgumentException("Duplicate category name, cannot save category");
        }
        cat.getWeblog().invalidateCache();
        this.strategy.store(cat);
    }

    @Override
    public void removeWeblogCategory(WeblogCategory cat) {

        WeblogEntrySearchCriteria wesc = new WeblogEntrySearchCriteria();
        wesc.setWeblog(cat.getWeblog());
        wesc.setCategoryName(cat.getName());

        if (weblogEntryManager.getWeblogEntries(wesc).size() > 0) {
            throw new IllegalStateException("Cannot remove category with entries");
        }

        cat.getWeblog().getWeblogCategories().remove(cat);
        cat.getWeblog().invalidateCache();
        this.strategy.remove(cat);
    }

    @Override
    public void moveWeblogCategoryContents(WeblogCategory srcCat, WeblogCategory destCat) {

        // get all entries in category and subcats
        WeblogEntrySearchCriteria wesc = new WeblogEntrySearchCriteria();
        wesc.setWeblog(srcCat.getWeblog());
        wesc.setCategoryName(srcCat.getName());
        List<WeblogEntry> results = weblogEntryManager.getWeblogEntries(wesc);

        // Loop through entries in src cat, assign them to dest cat
        Weblog website = destCat.getWeblog();
        for (WeblogEntry entry : results) {
            entry.setCategory(destCat);
            entry.setWeblog(website);
            this.strategy.store(entry);
        }
    }

    @Override
    public List<WeblogCategory> getWeblogCategories(Weblog weblog) {
        if (weblog == null) {
            throw new IllegalArgumentException("weblog is null");
        }

        TypedQuery<WeblogCategory> q = strategy.getNamedQuery(
                "WeblogCategory.getByWeblog", WeblogCategory.class);
        q.setParameter(1, weblog);
        return q.getResultList();
    }

    @Override
    public WeblogCategory getWeblogCategory(String id) {
        return this.strategy.load(WeblogCategory.class, id);
    }

    //--------------------------------------------- WeblogCategory Queries

    @Override
    public WeblogCategory getWeblogCategoryByName(Weblog weblog, String categoryName) {
        TypedQuery<WeblogCategory> q = strategy.getNamedQuery(
                "WeblogCategory.getByWeblog&Name", WeblogCategory.class);
        q.setParameter(1, weblog);
        q.setParameter(2, categoryName);
        q.setHint("javax.persistence.cache.storeMode", "REFRESH");
        try {
            return q.getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    @Override
    public boolean isWeblogCategoryInUse(WeblogCategory cat) {
        TypedQuery<WeblogEntry> q = strategy.getNamedQuery("WeblogEntry.getByCategory", WeblogEntry.class);
        q.setParameter(1, cat);
        int entryCount = q.getResultList().size();
        return entryCount > 0;
    }

    @Override
    public Blacklist getWeblogBlacklist(Weblog weblog) {
        if (StringUtils.isEmpty(weblog.getBlacklist())) {
            // just rely on the global blacklist if no overrides
            return getSiteBlacklist();
        } else {
            Blacklist bl = (Blacklist) weblogBlacklistCache.get(weblog.getHandle(),
                    weblog.getLastModified().toEpochMilli());

            if (bl == null) {
                bl = new Blacklist(weblog.getBlacklist(), getSiteBlacklist());
                weblogBlacklistCache.put(weblog.getHandle(), bl);
            }

            return bl;
        }
    }

    @Override
    public List<WeblogEntryTagAggregate> getPopularTags(Weblog weblog, int offset, int limit) {
        TypedQuery<WeblogEntryTagAggregate> query;
        List queryResults;
        int queryLimit = (limit >= 0) ? limit : 25;

        query = strategy.getNamedQuery("WeblogEntryTagAggregate.getPopularTagsByWeblog", WeblogEntryTagAggregate.class);
        query.setParameter(1, weblog);

        if (offset != 0) {
            query.setFirstResult(offset);
        }
        if (limit != -1) {
            query.setMaxResults(queryLimit);
        }
        queryResults = query.getResultList();

        double min = Integer.MAX_VALUE;
        double max = Integer.MIN_VALUE;

        List<WeblogEntryTagAggregate> results = new ArrayList<>(queryLimit);

        for (Object obj : queryResults) {
            Object[] row = (Object[]) obj;
            WeblogEntryTagAggregate t = new WeblogEntryTagAggregate();
            t.setName((String) row[0]);
            t.setTotal(((Number) row[1]).intValue());

            min = Math.min(min, t.getTotal());
            max = Math.max(max, t.getTotal());
            results.add(t);
        }

        min = Math.log(1 + min);
        max = Math.log(1 + max);

        double range = Math.max(.01, max - min) * 1.0001;
        for (WeblogEntryTagAggregate t : results) {
            t.setIntensity((int) (1 + Math.floor(5 * (Math.log(1 + t.getTotal()) - min) / range)));
        }

        // sort results by name, because query had to sort by total
        Collections.sort(results, WeblogEntryTagAggregate.comparator);

        return results;
    }

    @Override
    public List<WeblogEntryTagAggregate> getTags(Weblog website, String sortBy, String startsWith, int offset, int limit) {
        Query query;
        List queryResults;
        boolean sortByName = sortBy == null || !sortBy.equals("count");

        List<Object> params = new ArrayList<>();
        int size = 0;
        StringBuilder queryString = new StringBuilder();
        queryString.append("SELECT w.name, SUM(w.total) FROM WeblogEntryTagAggregate w WHERE 1 = 1");

        if (website != null) {
            params.add(size++, website.getId());
            queryString.append(" AND w.weblog.id = ?").append(size);
        }

        if (startsWith != null && startsWith.length() > 0) {
            params.add(size++, startsWith + '%');
            queryString.append(" AND w.name LIKE ?").append(size);
        }

        if (sortBy != null && sortBy.equals("count")) {
            sortBy = "w.total DESC";
        } else {
            sortBy = "w.name";
        }
        queryString.append(" GROUP BY w.name ORDER BY ").append(sortBy);

        query = strategy.getDynamicQuery(queryString.toString());
        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }
        if (offset != 0) {
            query.setFirstResult(offset);
        }
        if (limit != -1) {
            query.setMaxResults(limit);
        }
        queryResults = query.getResultList();

        List<WeblogEntryTagAggregate> results = new ArrayList<>();
        if (queryResults != null) {
            for (Object obj : queryResults) {
                Object[] row = (Object[]) obj;
                WeblogEntryTagAggregate ce = new WeblogEntryTagAggregate();
                ce.setName((String) row[0]);
                // The JPA query retrieves SUM(w.total) always as long
                ce.setTotal(((Long) row[1]).intValue());
                results.add(ce);
            }
        }

        if (sortByName) {
            Collections.sort(results, WeblogEntryTagAggregate.comparator);
        } else {
            Collections.sort(results, WeblogEntryTagAggregate.countComparator);
        }

        return results;
    }

    @Override
    public boolean getTagExists(Weblog weblog, String tag) {
        if (tag == null) {
            return false;
        }

        List<Object> params = new ArrayList<>(2);
        StringBuilder queryString = new StringBuilder();
        queryString.append("SELECT DISTINCT w.name ");
        queryString.append("FROM WeblogEntryTagAggregate w WHERE w.name = ?1");
        params.add(tag);

        if (weblog != null) {
            queryString.append(" AND w.weblog = ?2");
            params.add(weblog);
        }

        TypedQuery<String> q = strategy.getDynamicQuery(queryString.toString(), String.class);
        for (int j = 0; j < params.size(); j++) {
            q.setParameter(j + 1, params.get(j));
        }
        List<String> results = q.getResultList();

        // OK if at least one article matches the tag
        return (results != null && results.size() > 0);
    }

    @Override
    public void removeTag(Weblog weblog, String tagName) {
        Query removeCategories = strategy.getNamedUpdate("WeblogEntryTag.removeByWeblogAndTagName");
        removeCategories.setParameter(1, weblog);
        removeCategories.setParameter(2, tagName);
        removeCategories.executeUpdate();
        weblog.invalidateCache();
        strategy.flush();
    }

    @Override
    public Map<String, Integer> addTag(Weblog weblog, String currentTagName, String newTagName) {
        Map<String, Integer> resultsMap = new HashMap<>();
        int updatedEntries = 0;
        int unchangedEntries = 0;

        TypedQuery<WeblogEntryTag> currentTagQuery = strategy.getNamedQuery("WeblogEntryTag.getByWeblogAndName",
                WeblogEntryTag.class);
        currentTagQuery.setParameter(1, weblog);
        currentTagQuery.setParameter(2, currentTagName);
        List<WeblogEntryTag> currentResults = currentTagQuery.getResultList();

        TypedQuery<String> alreadyHasNewTagQuery = strategy.getNamedQuery("WeblogEntryTag.getEntryIdByWeblogAndName",
                String.class);
        alreadyHasNewTagQuery.setParameter(1, weblog);
        alreadyHasNewTagQuery.setParameter(2, newTagName);
        List<String> alreadyEntryIdList = alreadyHasNewTagQuery.getResultList();

        for (WeblogEntryTag currentTag : currentResults) {
            if (alreadyEntryIdList.contains(currentTag.getWeblogEntry().getId())) {
                unchangedEntries++;
            } else {
                WeblogEntryTag newTag = new WeblogEntryTag(weblog, currentTag.getWeblogEntry(), newTagName);
                strategy.store(newTag);
                updatedEntries++;
            }
        }

        strategy.flush();
        resultsMap.put("updated", updatedEntries);
        resultsMap.put("unchanged", unchangedEntries);
        return resultsMap;
    }

    public Blacklist getSiteBlacklist() {
        if (siteBlacklist == null) {
            WebloggerProperties props = strategy.getWebloggerProperties();
            siteBlacklist = new Blacklist(props.getCommentSpamFilter(), null);
        }
        return siteBlacklist;
    }

    public void setSiteBlacklist(Blacklist siteBlacklist) {
        this.siteBlacklist = siteBlacklist;
    }

}
