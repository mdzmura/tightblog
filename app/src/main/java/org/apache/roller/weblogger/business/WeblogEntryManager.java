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
package org.apache.roller.weblogger.business;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.roller.weblogger.pojos.CommentSearchCriteria;
import org.apache.roller.weblogger.pojos.Weblog;
import org.apache.roller.weblogger.pojos.WeblogEntry;
import org.apache.roller.weblogger.pojos.WeblogEntryComment;
import org.apache.roller.weblogger.pojos.WeblogEntrySearchCriteria;
import org.apache.roller.weblogger.pojos.WeblogEntryTagAggregate;

/**
 * Interface to weblog entry, category and comment management.
 */
public interface WeblogEntryManager {

    /**
     * Save weblog entry.
     */
    void saveWeblogEntry(WeblogEntry entry);
       
    /**
     * Remove weblog entry.
     */
    void removeWeblogEntry(WeblogEntry entry);
    
    /**
     * Get weblog entry by id.
     */
    WeblogEntry getWeblogEntry(String id);
    
    /** 
     * Get weblog entry by anchor. 
     */
    WeblogEntry getWeblogEntryByAnchor(Weblog website, String anchor);
        
    /**
     * Get WeblogEntries by offset/length as list in reverse chronological order.
     * The range offset and list arguments enable paging through query results.
     * @param wesc WeblogEntrySearchCriteria object listing desired search parameters
     * @return List of WeblogEntry objects in order specified by search criteria
     */
    List<WeblogEntry> getWeblogEntries(WeblogEntrySearchCriteria wesc);

    /**
     * Get Weblog Entries grouped by day.
     * @param wesc WeblogEntrySearchCriteria object listing desired search parameters
     * @return Map of Lists of WeblogEntries keyed by calendar day
     */
    Map<LocalDate, List<WeblogEntry>> getWeblogEntryObjectMap(WeblogEntrySearchCriteria wesc);

    /**
     * Get Weblog Entry date strings grouped by day. This method returns a Map
     * that contains one YYYYMMDD date string object for each calendar day having
     * one or more blog entries.
     * @param wesc WeblogEntrySearchCriteria object listing desired search parameters
     * @return Map of date strings keyed by Date
     */
    Map<LocalDate, String> getWeblogEntryStringMap(WeblogEntrySearchCriteria wesc);
    
    /**
     * Get the WeblogEntry following, chronologically, the current entry.
     * Restrict by the Category, if named.
     * @param current The "current" WeblogEntryData
     * @param catName The value of the requested Category Name
     */
    WeblogEntry getNextEntry(WeblogEntry current, String catName);
    
    /**
     * Get the WeblogEntry prior to, chronologically, the current entry.
     * Restrict by the Category, if named.
     * @param current The "current" WeblogEntryData.
     * @param catName The value of the requested Category Name.
     */
    WeblogEntry getPreviousEntry(WeblogEntry current, String catName);
      
    /**
     * Save comment.
     * @param refreshWeblog true if weblog should be marked for cache update, i.e., likely
     *                      rendering change to accommodate new or removed comment, vs. one
     *                      still requiring moderation.
     */
    void saveComment(WeblogEntryComment comment, boolean refreshWeblog);
    
    /**
     * Remove comment.
     */
    void removeComment(WeblogEntryComment comment);
   
    /**
     * Get comment by id.
     */
    WeblogEntryComment getComment(String id);
       
    /**
     * Generic comments query method.
     * @param csc CommentSearchCriteria object with fields indicating search criteria
     * @return list of comments fitting search criteria
     */
    List<WeblogEntryComment> getComments(CommentSearchCriteria csc);

    /**
     * Create unique anchor for weblog entry.
     */
    String createAnchor(WeblogEntry data);

    /**
     * Apply comment default settings from website to all of website's entries.
     */
    void applyCommentDefaultsToEntries(Weblog website);
    
    /**
     * Get list of WeblogEntryTagAggregate objects identifying the most used tags for a weblog.
     * There are no offset/length params just a limit.
     * @param weblog       Weblog or null to get for all weblogs.
     * @param offset       0-based index into results
     * @param limit        Max objects to return (or -1 for no limit)
     * @return List of most popular tags.
     */
    List<WeblogEntryTagAggregate> getPopularTags(Weblog weblog, int offset, int limit);
    
    /**
     * Get list of WeblogEntryTagAggregate objects for the tags comprising a weblog. There are
     * no offset/length params just a limit.
     * @param website       Weblog or null to get for all weblogs.
     * @param sortBy        Sort by either 'name' or 'count' (null for name) 
     * @param startsWith    Prefix for tags to be returned (null or a string of length > 0)
     * @param limit         Max objects to return (or -1 for no limit)
     * @return List of tags matching the criteria.
     */
    List<WeblogEntryTagAggregate> getTags(Weblog website, String sortBy, String startsWith, int offset, int limit);
    
    /**
     * Does the specified tag exist?  Optionally confined to a specific weblog.
     *
     * This checks if the Weblog (or all weblogs) has at least one blog entry with
     * the given tag.
     *
     * @param tag The tag to search for.
     * @param weblog The weblog to confine the check to.
     * @return True if the tag exists, false otherwise.
     */
    boolean getTagExists(String tag, Weblog weblog);
    
    /**
     * Get site-wide comment count 
     */
    long getCommentCount();
    
    /**
     * Get weblog comment count 
     */    
    long getCommentCount(Weblog websiteData);
    
    /**
     * Get site-wide entry count 
     */    
    long getEntryCount();
    
    /**
     * Get weblog entry count 
     */    
    long getEntryCount(Weblog websiteData);

    /**
     * Apply a set of weblog entry plugins to the specified string and
     * return the results.  This method must *NOT* alter the contents of
     * the original entry object.
     *
     * @param entry       Original weblog entry
     * @param str         String to which to apply plugins
     * @return        the transformed text
     */
    String applyWeblogEntryPlugins(WeblogEntry entry, String str);

    /**
     * Apply comment plugins.
     *
     * @param comment The comment to apply plugins for.
     * @param text The text to apply the plugins to.
     * @return String The transformed comment text.
     */
    String applyCommentPlugins(WeblogEntryComment comment, String text);

}
