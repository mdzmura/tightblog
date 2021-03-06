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
package org.apache.roller.weblogger.ui.rendering.comment;

import org.apache.roller.weblogger.WebloggerTest;
import org.apache.roller.weblogger.pojos.WeblogEntryComment;
import org.apache.roller.weblogger.pojos.User;
import org.apache.roller.weblogger.pojos.WeblogEntry;
import org.apache.roller.weblogger.pojos.Weblog;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class CommentValidatorTest extends WebloggerTest {
    Weblog weblog = null;
    User testUser = null;
    WeblogEntry entry = null;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        testUser = setupUser("johndoe");
        weblog = setupWeblog("doeblog", testUser);
        entry = setupWeblogEntry("anchor1", weblog, testUser);
        endSession(true);
    }
    
    @After
    public void tearDown() throws Exception {
        teardownWeblogEntry(entry.getId());
        teardownWeblog(weblog.getId());
        teardownUser(testUser.getId());
    }

    @Test
    public void testExcessSizeCommentValidator() {
        ExcessSizeCommentValidator validator = new ExcessSizeCommentValidator();
        Map<String, List<String>> msgs = new HashMap<>();
        WeblogEntryComment comment = createEmptyComment();

        // string that exceeds default excess size threshold of 1000
        StringBuilder sb = new StringBuilder();
        for (int i=0; i<101; i++) {
            sb.append("0123456789");
        }
        
        comment.setContent("short stuff"); 
        assertEquals(100, validator.validate(comment, msgs));

        comment.setContent(sb.toString()); 
        assertTrue(validator.validate(comment, msgs) != 100);
    }

    @Test
    public void testExcessLinksCommentValidator() {
        ExcessLinksCommentValidator validator = new ExcessLinksCommentValidator();

        Map<String, List<String>> msgs = new HashMap<>();
        WeblogEntryComment comment = createEmptyComment();
        
        comment.setContent("<a href=\"http://example.com\">link1</a>"); 
        assertEquals(100, validator.validate(comment, msgs));

        // String that exceeds default excess links threshold of 3
        comment.setContent(
            "<a href=\"http://example.com\">link1</a>" +
            "<a href=\"http://example.com\">link2</a>" +
            "<a href=\"http://example.com\">link3</a>" +
            "<a href=\"http://example.com\">link4</a>" +
            "<a href=\"http://example.com\">link5</a>"
        ); 
        assertTrue(validator.validate(comment, msgs) != 100);
    }

    @Test
    public void testBlacklistCommentValidator() {
        BlacklistCommentValidator validator = new BlacklistCommentValidator();
        validator.setWeblogManager(weblogManager);
        Map<String, List<String>> msgs = new HashMap<>();

        WeblogEntryComment comment = createEmptyComment();
        comment.getWeblogEntry().getWeblog().setBlacklist("www.myblacklistedsite.com");

        comment.setContent("nice friendly stuff");
        assertEquals(100, validator.validate(comment, msgs));

        comment.setContent("blah blah www.myblacklistedsite.com blah");
        assertTrue(validator.validate(comment, msgs) != 100);
    }
    
    // To run this test provide your Akismet API key below.
    public void testAkismetCommentValidator() {
        AkismetCommentValidator validator = new AkismetCommentValidator(urlStrategy, "api code here");

        Map<String, List<String>> msgs = new HashMap<>();
        WeblogEntryComment comment = createEmptyComment();
        comment.setContent("nice friendly stuff");

        assertEquals(100, validator.validate(comment, msgs));

            // per Akismet docs, name hardcoded to always fail
        comment.setName("viagra-test-123");
        assertTrue(validator.validate(comment, msgs) != 100);
    }

    private WeblogEntryComment createEmptyComment() {
        WeblogEntryComment comment = new WeblogEntryComment();
        comment.setUrl("http://example.com");
        comment.setName("Mortimer Snerd");
        comment.setEmail("mortimer@snerd.com");
        comment.setWeblogEntry(entry);
        return comment;
    }
}
