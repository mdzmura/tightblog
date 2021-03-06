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

package org.apache.roller.weblogger.ui.rendering.pagers;

import org.apache.roller.weblogger.business.URLStrategy;
import org.apache.roller.weblogger.pojos.Weblog;
import org.apache.roller.weblogger.pojos.WeblogEntry;
import org.apache.roller.weblogger.ui.rendering.requests.WeblogSearchRequest;
import org.apache.roller.weblogger.util.I18nMessages;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Pager for navigating through search results.
 */
public class WeblogEntriesSearchPager implements WeblogEntriesPager {

    // message utils for doing i18n messages
    private I18nMessages messageUtils = null;

    // url strategy
    private URLStrategy urlStrategy = null;

    private Map<LocalDate, List<WeblogEntry>> entries = null;

    private Weblog weblog = null;
    private String query = null;
    private String category = null;
    private int page = 0;
    private boolean moreResults = false;

    public WeblogEntriesSearchPager(URLStrategy strat, WeblogSearchRequest searchRequest,
                                    Map<LocalDate, List<WeblogEntry>> entries, boolean more) {

        // url strategy for building urls
        this.urlStrategy = strat;

        // store search results
        this.entries = entries;

        // data from search request
        this.weblog = searchRequest.getWeblog();
        this.query = searchRequest.getQuery();
        this.category = searchRequest.getWeblogCategoryName();
        this.page = searchRequest.getPageNum();

        // does this pager have more results?
        this.moreResults = more;

        // get a message utils instance to handle i18n of messages
        Locale viewLocale = weblog.getLocaleInstance();
        this.messageUtils = I18nMessages.getMessages(viewLocale);
    }

    public Map<LocalDate, List<WeblogEntry>> getEntries() {
        return entries;
    }

    public List<WeblogEntry> getItems() {
        return entries.values().stream().flatMap(List::stream).collect(Collectors.toList());
    }

    public String getHomeLink() {
        return urlStrategy.getWeblogURL(weblog, false);
    }

    public String getHomeName() {
        return messageUtils.getString("weblogEntriesPager.search.home");
    }

    public String getNextLink() {
        if (moreResults) {
            return urlStrategy.getWeblogSearchURL(weblog, query, category, page + 1, false);
        }
        return null;
    }

    public String getNextName() {
        if (getNextLink() != null) {
            return messageUtils.getString("weblogEntriesPager.search.next");
        }
        return null;
    }

    public String getPrevLink() {
        if (page > 0) {
            return urlStrategy.getWeblogSearchURL(weblog, query, category, page - 1, false);
        }
        return null;
    }

    public String getPrevName() {
        if (getPrevLink() != null) {
            return messageUtils.getString("weblogEntriesPager.search.prev");
        }
        return null;
    }

    public String getNextCollectionLink() {
        return null;
    }

    public String getNextCollectionName() {
        return null;
    }

    public String getPrevCollectionLink() {
        return null;
    }

    public String getPrevCollectionName() {
        return null;
    }

}
