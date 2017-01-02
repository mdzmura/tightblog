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
import org.apache.roller.weblogger.business.PingTargetManager;
import org.apache.roller.weblogger.business.PropertiesManager;
import org.apache.roller.weblogger.business.WeblogEntryManager;
import org.apache.roller.weblogger.business.WeblogManager;
import org.apache.roller.weblogger.pojos.AtomEnclosure;
import org.apache.roller.weblogger.pojos.CommentSearchCriteria;
import org.apache.roller.weblogger.pojos.Weblog;
import org.apache.roller.weblogger.pojos.WeblogCategory;
import org.apache.roller.weblogger.pojos.WeblogEntry;
import org.apache.roller.weblogger.pojos.WeblogEntry.PubStatus;
import org.apache.roller.weblogger.pojos.WeblogEntryComment;
import org.apache.roller.weblogger.pojos.WeblogEntryComment.ApprovalStatus;
import org.apache.roller.weblogger.pojos.WeblogEntrySearchCriteria;
import org.apache.roller.weblogger.util.HTMLSanitizer;
import org.apache.roller.weblogger.util.Utilities;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.NoResultException;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;

public class JPAWeblogEntryManagerImpl implements WeblogEntryManager {

    private static Logger log = LoggerFactory.getLogger(JPAWeblogEntryManagerImpl.class);

    private final PingTargetManager pingTargetManager;
    private final JPAPersistenceStrategy strategy;

    private WeblogManager weblogManager;

    public void setWeblogManager(WeblogManager weblogManager) {
        this.weblogManager = weblogManager;
    }

    private PropertiesManager propertiesManager;

    public void setPropertiesManager(PropertiesManager propertiesManager) {
        this.propertiesManager = propertiesManager;
    }

    // cached mapping of entryAnchors -> entryIds
    private Map<String, String> entryAnchorToIdMap = Collections.synchronizedMap(new HashMap<>());

    protected JPAWeblogEntryManagerImpl(PingTargetManager mgr, JPAPersistenceStrategy strategy) {
        log.debug("Instantiating JPA Weblog Manager");
        this.pingTargetManager = mgr;
        this.strategy = strategy;
    }

    @Override
    public void saveComment(WeblogEntryComment comment, boolean refreshWeblog) {
        if (refreshWeblog) {
            comment.getWeblogEntry().getWeblog().invalidateCache();
        }
        this.strategy.store(comment);
    }

    @Override
    public void removeComment(WeblogEntryComment comment) {
        comment.getWeblogEntry().getWeblog().invalidateCache();
        this.strategy.remove(comment);
    }

    @Override
    public void saveWeblogEntry(WeblogEntry entry) {

        if (entry.getCategory() == null) {
            // Entry is invalid without category, so use first one found if not provided
            WeblogCategory cat = entry.getWeblog().getWeblogCategories().iterator().next();
            entry.setCategory(cat);
        }

        if (entry.getAnchor() == null || entry.getAnchor().trim().equals("")) {
            entry.setAnchor(this.createAnchor(entry));
        }

        // if the entry was published to future, set status as SCHEDULED
        // we only consider an entry future published if it is scheduled
        // more than 1 minute into the future
        if (PubStatus.PUBLISHED.equals(entry.getStatus()) &&
                entry.getPubTime().isAfter(Instant.now().plus(1, ChronoUnit.MINUTES))) {
            entry.setStatus(PubStatus.SCHEDULED);
        }

        // Store value object (creates new or updates existing)
        entry.setUpdateTime(Instant.now());

        this.strategy.store(entry);
        entry.getWeblog().invalidateCache();

        if (entry.isPublished()) {
            strategy.store(entry.getWeblog());
        }

        if (entry.isPublished()) {
            // Queue applicable pings for this update.
            pingTargetManager.addToPingSet(entry.getWeblog());
        }

    }

    @Override
    public void removeWeblogEntry(WeblogEntry entry) {
        CommentSearchCriteria csc = new CommentSearchCriteria();
        csc.setEntry(entry);

        // remove comments
        List<WeblogEntryComment> comments = getComments(csc);
        for (WeblogEntryComment comment : comments) {
            this.strategy.remove(comment);
        }

        // remove entry
        this.strategy.remove(entry);
        entry.getWeblog().invalidateCache();

        // remove entry from cache mapping
        this.entryAnchorToIdMap.remove(entry.getWeblog().getHandle() + ":" + entry.getAnchor());
    }

    private List<WeblogEntry> getNextPrevEntries(WeblogEntry current, String catName, int maxEntries, boolean next) {

        if (current == null || current.getPubTime() == null) {
            return Collections.emptyList();
        }

        TypedQuery<WeblogEntry> query;
        WeblogCategory category;

        List<Object> params = new ArrayList<>();
        int size = 0;
        String queryString = "SELECT e FROM WeblogEntry e WHERE ";
        StringBuilder whereClause = new StringBuilder();

        params.add(size++, current.getWeblog());
        whereClause.append("e.weblog = ?").append(size);

        params.add(size++, PubStatus.PUBLISHED);
        whereClause.append(" AND e.status = ?").append(size);

        if (next) {
            params.add(size++, current.getPubTime());
            whereClause.append(" AND e.pubTime > ?").append(size);
        } else {
            params.add(size++, current.getPubTime());
            whereClause.append(" AND e.pubTime < ?").append(size);
        }

        if (catName != null) {
            category = weblogManager.getWeblogCategoryByName(current.getWeblog(), catName);
            if (category != null) {
                params.add(size++, category);
                whereClause.append(" AND e.category = ?").append(size);
            }
        }

        if (next) {
            whereClause.append(" ORDER BY e.pubTime ASC");
        } else {
            whereClause.append(" ORDER BY e.pubTime DESC");
        }
        query = strategy.getDynamicQuery(queryString + whereClause.toString(), WeblogEntry.class);
        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }
        query.setMaxResults(maxEntries);

        return query.getResultList();
    }

    @Override
    public List<WeblogEntry> getWeblogEntries(WeblogEntrySearchCriteria criteria) {
        List<Object> params = new ArrayList<>();
        int size = 0;
        StringBuilder queryString = new StringBuilder();

        if (criteria.getTags() == null || criteria.getTags().size() == 0) {
            queryString.append("SELECT e FROM WeblogEntry e WHERE 1=1 ");
        } else {
            // subquery to avoid this problem with Derby: http://stackoverflow.com/a/480536
            queryString.append("SELECT e FROM WeblogEntry e WHERE EXISTS ( Select 1 from WeblogEntryTag t " +
                    "where t.weblogEntry.id = e.id AND ");
            queryString.append("(");
            boolean isFirst = true;
            for (String tagName : criteria.getTags()) {
                if (!isFirst) {
                    queryString.append(" OR ");
                }
                params.add(size++, tagName);
                queryString.append(" t.name = ?").append(size);
                isFirst = false;
            }
            queryString.append(")) ");
        }

        if (criteria.getWeblog() != null) {
            params.add(size++, criteria.getWeblog().getId());
            queryString.append("AND e.weblog.id = ?").append(size);
        }

        params.add(size++, Boolean.TRUE);
        queryString.append(" AND e.weblog.visible = ?").append(size);

        if (criteria.getUser() != null) {
            params.add(size++, criteria.getUser().getUserName());
            queryString.append(" AND e.creatorUserName = ?").append(size);
        }

        if (criteria.getStartDate() != null) {
            params.add(size++, criteria.getStartDate());
            queryString.append(" AND e.pubTime >= ?").append(size);
        }

        if (criteria.getEndDate() != null) {
            params.add(size++, criteria.getEndDate());
            queryString.append(" AND e.pubTime <= ?").append(size);
        }

        if (!StringUtils.isEmpty(criteria.getCategoryName())) {
            params.add(size++, criteria.getCategoryName());
            queryString.append(" AND e.category.name = ?").append(size);
        }

        if (criteria.getStatus() != null) {
            params.add(size++, criteria.getStatus());
            queryString.append(" AND e.status = ?").append(size);
        }

        if (StringUtils.isNotEmpty(criteria.getText())) {
            params.add(size++, '%' + criteria.getText() + '%');
            queryString.append(" AND ( e.text LIKE ?").append(size);
            queryString.append("    OR e.summary LIKE ?").append(size);
            queryString.append("    OR e.title LIKE ?").append(size);
            queryString.append(") ");
        }

        if (criteria.getSortBy() != null && criteria.getSortBy().equals(WeblogEntrySearchCriteria.SortBy.UPDATE_TIME)) {
            queryString.append(" ORDER BY e.updateTime ");
        } else {
            queryString.append(" ORDER BY e.pubTime ");
        }

        if (criteria.getSortOrder() != null && criteria.getSortOrder().equals(WeblogEntrySearchCriteria.SortOrder.ASCENDING)) {
            queryString.append("ASC ");
        } else {
            queryString.append("DESC ");
        }

        TypedQuery<WeblogEntry> query = strategy.getDynamicQuery(queryString.toString(), WeblogEntry.class);
        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }

        if (criteria.getOffset() != 0) {
            query.setFirstResult(criteria.getOffset());
        }
        if (criteria.getMaxResults() != -1) {
            query.setMaxResults(criteria.getMaxResults());
        }

        return query.getResultList();
    }

    @Override
    public WeblogEntry getWeblogEntryByAnchor(Weblog website, String anchor) {

        if (website == null) {
            throw new IllegalArgumentException("Website is null");
        }

        if (anchor == null) {
            throw new IllegalArgumentException("Anchor is null");
        }

        // mapping key is combo of weblog + anchor
        String mappingKey = website.getHandle() + ":" + anchor;

        // check cache first
        // NOTE: if we ever allow changing anchors then this needs updating
        if (this.entryAnchorToIdMap.containsKey(mappingKey)) {

            WeblogEntry entry = this.getWeblogEntry(this.entryAnchorToIdMap.get(mappingKey), false);
            if (entry != null) {
                log.debug("entryAnchorToIdMap CACHE HIT - {}", mappingKey);
                return entry;
            } else {
                // mapping hit with lookup miss?  mapping must be old, remove it
                this.entryAnchorToIdMap.remove(mappingKey);
            }
        }

        // cache failed, do lookup
        TypedQuery<WeblogEntry> q = strategy.getNamedQuery(
                "WeblogEntry.getByWeblog&AnchorOrderByPubTimeDesc", WeblogEntry.class);
        q.setParameter(1, website);
        q.setParameter(2, anchor);
        WeblogEntry entry;
        try {
            entry = q.getSingleResult();
        } catch (NoResultException e) {
            entry = null;
        }

        // add mapping to cache
        if (entry != null) {
            log.debug("entryAnchorToIdMap CACHE MISS - {}", mappingKey);
            this.entryAnchorToIdMap.put(mappingKey, entry.getId());
        }
        return entry;
    }

    @Override
    public String createAnchor(WeblogEntry entry) {
        // Check for uniqueness of anchor
        String base = createAnchorBase(entry);
        String name = base;
        int count = 0;

        while (true) {
            if (count > 0) {
                name = base + count;
            }

            TypedQuery<WeblogEntry> q = strategy.getNamedQuery("WeblogEntry.getByWeblog&Anchor", WeblogEntry.class);
            q.setParameter(1, entry.getWeblog());
            q.setParameter(2, name);
            List results = q.getResultList();

            if (results.size() < 1) {
                break;
            } else {
                count++;
            }
        }
        return name;
    }

    /**
     * Create anchor for weblog entry, based on title or text
     */
    private String createAnchorBase(WeblogEntry entry) {
        // Use title (minus non-alphanumeric characters)
        String base;
        if (!StringUtils.isEmpty(entry.getTitle())) {
            base = Utilities.replaceNonAlphanumeric(entry.getTitle(), ' ').trim();
        } else {
            // try text
            base = Utilities.replaceNonAlphanumeric(entry.getText(), ' ').trim();
        }

        // Use only the first 4 words
        StringTokenizer toker = new StringTokenizer(base);
        String tmp = null;
        int count = 0;
        while (toker.hasMoreTokens() && count < 5) {
            String s = toker.nextToken();
            s = s.toLowerCase();
            tmp = (tmp == null) ? s : tmp + "-" + s;
            count++;
        }
        base = tmp;

        return base;
    }

    @Override
    public List<WeblogEntryComment> getComments(CommentSearchCriteria csc) {

        List<Object> params = new ArrayList<>();
        int size = 0;
        StringBuilder queryString = new StringBuilder();
        queryString.append("SELECT c FROM WeblogEntryComment c");

        StringBuilder whereClause = new StringBuilder();
        if (csc.getEntry() != null) {
            params.add(size++, csc.getEntry());
            appendConjuctionToWhereclause(whereClause, "c.weblogEntry = ?").append(size);
        } else {
            if (csc.getWeblog() != null) {
                params.add(size++, csc.getWeblog());
                appendConjuctionToWhereclause(whereClause, "c.weblogEntry.weblog = ?").append(size);
            }
            if (csc.getCategoryName() != null) {
                params.add(size++, csc.getCategoryName());
                appendConjuctionToWhereclause(whereClause, "c.weblogEntry.category.name = ?").append(size);
            }
        }

        if (csc.getSearchText() != null) {
            params.add(size++, "%" + csc.getSearchText().toUpperCase() + "%");
            appendConjuctionToWhereclause(whereClause, "upper(c.content) LIKE ?").append(size);
        }

        if (csc.getStartDate() != null) {
            params.add(size++, csc.getStartDate());
            appendConjuctionToWhereclause(whereClause, "c.postTime >= ?").append(size);
        }

        if (csc.getEndDate() != null) {
            params.add(size++, csc.getEndDate());
            appendConjuctionToWhereclause(whereClause, "c.postTime <= ?").append(size);
        }

        if (csc.getStatus() != null) {
            params.add(size++, csc.getStatus());
            appendConjuctionToWhereclause(whereClause, "c.status = ?").append(size);
        }

        if (whereClause.length() != 0) {
            queryString.append(" WHERE ").append(whereClause);
        }
        if (csc.isReverseChrono()) {
            queryString.append(" ORDER BY c.postTime DESC");
        } else {
            queryString.append(" ORDER BY c.postTime ASC");
        }

        TypedQuery<WeblogEntryComment> query = strategy.getDynamicQuery(queryString.toString(), WeblogEntryComment.class);
        if (csc.getOffset() != 0) {
            query.setFirstResult(csc.getOffset());
        }
        if (csc.getMaxResults() != -1) {
            query.setMaxResults(csc.getMaxResults());
        }
        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }
        return query.getResultList();

    }

    @Override
    public WeblogEntryComment getComment(String id) {
        return this.strategy.load(WeblogEntryComment.class, id);
    }

    @Override
    public WeblogEntry getWeblogEntry(String id, boolean bypassCache) {
        return strategy.load(WeblogEntry.class, id, bypassCache);
    }

    @Override
    public Map<LocalDate, List<WeblogEntry>> getWeblogEntryObjectMap(WeblogEntrySearchCriteria wesc) {
        TreeMap<LocalDate, List<WeblogEntry>> map = new TreeMap<>(Collections.reverseOrder());

        List<WeblogEntry> entries = getWeblogEntries(wesc);

        for (WeblogEntry entry : entries) {
            LocalDate tmp = entry.getPubTime() == null ? LocalDate.now() :
                    entry.getPubTime().atZone(ZoneId.systemDefault()).toLocalDate();
            List<WeblogEntry> dayEntries = map.get(tmp);
            if (dayEntries == null) {
                dayEntries = new ArrayList<>();
                map.put(tmp, dayEntries);
            }
            dayEntries.add(entry);
        }
        return map;
    }

    @Override
    public Map<LocalDate, String> getWeblogEntryStringMap(WeblogEntrySearchCriteria wesc) {
        TreeMap<LocalDate, String> map = new TreeMap<>(Collections.reverseOrder());

        List<WeblogEntry> entries = getWeblogEntries(wesc);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(Utilities.FORMAT_8CHARS);

        for (WeblogEntry entry : entries) {
            LocalDate maybeDate = entry.getPubTime().atZone(ZoneId.systemDefault()).toLocalDate();
            if (map.get(maybeDate) == null) {
                map.put(maybeDate, formatter.format(maybeDate));
            }
        }
        return map;
    }

    @Override
    public WeblogEntry getNextEntry(WeblogEntry current, String catName) {
        WeblogEntry entry = null;
        List<WeblogEntry> entryList = getNextPrevEntries(current, catName, 1, true);
        if (entryList != null && entryList.size() > 0) {
            entry = entryList.get(0);
        }
        return entry;
    }

    @Override
    public WeblogEntry getPreviousEntry(WeblogEntry current, String catName) {
        WeblogEntry entry = null;
        List<WeblogEntry> entryList = getNextPrevEntries(current, catName, 1, false);
        if (entryList != null && entryList.size() > 0) {
            entry = entryList.get(0);
        }
        return entry;
    }

    @Override
    public void applyCommentDefaultsToEntries(Weblog weblog) {
        Query q = strategy.getNamedUpdate("WeblogEntry.updateCommentDaysByWeblog");
        q.setParameter(1, weblog.getDefaultCommentDays());
        q.setParameter(2, weblog);
        q.executeUpdate();
    }

    @Override
    public long getCommentCount() {
        TypedQuery<Long> q = strategy.getNamedQuery("WeblogEntryComment.getCountAllDistinctByStatus", Long.class);
        q.setParameter(1, ApprovalStatus.APPROVED);
        return q.getResultList().get(0);
    }

    @Override
    public long getCommentCount(Weblog weblog) {
        TypedQuery<Long> q = strategy.getNamedQuery("WeblogEntryComment.getCountDistinctByWeblog&Status", Long.class);
        q.setParameter(1, weblog);
        q.setParameter(2, ApprovalStatus.APPROVED);
        return q.getResultList().get(0);
    }

    @Override
    public long getEntryCount() {
        TypedQuery<Long> q = strategy.getNamedQuery("WeblogEntry.getCountDistinctByStatus", Long.class);
        q.setParameter(1, PubStatus.PUBLISHED);
        return q.getResultList().get(0);
    }

    @Override
    public long getEntryCount(Weblog website) {
        TypedQuery<Long> q = strategy.getNamedQuery("WeblogEntry.getCountDistinctByStatus&Weblog", Long.class);
        q.setParameter(1, PubStatus.PUBLISHED);
        q.setParameter(2, website);
        return q.getResultList().get(0);
    }

    /**
     * Appends given expression to given whereClause. If whereClause already
     * has other conditions, an " AND " is also appended before appending
     * the expression
     *
     * @param whereClause The given where Clauuse
     * @param expression  The given expression
     * @return the whereClause.
     */
    private static StringBuilder appendConjuctionToWhereclause(StringBuilder whereClause, String expression) {
        if (whereClause.length() != 0 && expression.length() != 0) {
            whereClause.append(" AND ");
        }
        return whereClause.append(expression);
    }

    @Override
    public String processBlogText(WeblogEntry entry, String str) {
        String ret = str;

        if (ret != null) {
            Parser parser = Parser.builder().build();
            Node document = parser.parse(ret);
            HtmlRenderer renderer = HtmlRenderer.builder().build();
            ret = renderer.render(document);
        }

        if (ret != null) {
            Whitelist whitelist = HTMLSanitizer.Level.valueOf(
                    propertiesManager.getStringProperty("site.html.whitelist")).getWhitelist();

            if (whitelist != null) {
                ret = Jsoup.clean(ret, whitelist);
            }
        }

        return ret;
    }

    @Override
    public AtomEnclosure generateEnclosure(String url) {
        if (url == null || url.trim().length() == 0) {
            return null;
        }

        AtomEnclosure resource;
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setRequestMethod("HEAD");
            int response = con.getResponseCode();
            String message = con.getResponseMessage();

            if (response != 200) {
                // Bad Response
                log.debug("Mediacast error {}:{} from url {}", response, message, url);
                throw new IllegalArgumentException("weblogEdit.mediaCastResponseError");
            } else {
                String contentType = con.getContentType();
                long length = con.getContentLength();

                if (contentType == null || length == -1) {
                    // Incomplete
                    log.debug("Response valid, but contentType or length is invalid");
                    throw new IllegalArgumentException("weblogEdit.mediaCastLacksContentTypeOrLength");
                }

                resource = new AtomEnclosure(url, contentType, length);
                log.debug("Valid mediacast resource = {}", resource.toString());

            }
        } catch (MalformedURLException mfue) {
            // Bad URL
            log.debug("Malformed MediaCast url: {}", url);
            throw new IllegalArgumentException("weblogEdit.mediaCastUrlMalformed", mfue);
        } catch (Exception e) {
            // Check Failed
            log.error("ERROR while checking MediaCast URL: {}: {}", url, e.getMessage());
            throw new IllegalArgumentException("weblogEdit.mediaCastFailedFetchingInfo", e);
        }
        return resource;
    }
}
