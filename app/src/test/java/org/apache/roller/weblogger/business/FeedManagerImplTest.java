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
package org.apache.roller.weblogger.business;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.WebloggerTest;
import org.apache.roller.weblogger.pojos.Planet;
import org.apache.roller.weblogger.pojos.Subscription;
import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.pojos.User;
import org.apache.roller.weblogger.pojos.Weblog;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.annotation.Resource;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

/**
 * Test custom weblogger feed fetcher.
 */
public class FeedManagerImplTest extends WebloggerTest {
    public static Log log = LogFactory.getLog(FeedManagerImplTest.class);
    
    String rollerFeedUrl = "weblogger:webloggerFetcherTestWeblog";
    String expectedTitle = "Slashdot";
    String expectedSiteUrl = "http://slashdot.org/";
    String externalFeedUrl = "http://rss.slashdot.org/Slashdot/slashdotMainatom";
    private Subscription testSub = null;
    private Planet planet = null;
    private User testUser = null;
    private Weblog testWeblog = null;

    @Resource
    protected FeedManager feedManager;

    public void setFeedManager(FeedManager feedManager) {
        this.feedManager = feedManager;
    }

    /**
     * All tests in this suite require a user and a weblog.
     */
    @Before
    public void setUp() throws Exception {
        super.setUp();
        testUser = setupUser("webloggerFetcherTestUser");
        testWeblog = setupWeblog("webloggerFetcherTestWeblog", testUser);

        // add test planet
        planet = new Planet("testPlanetHandle", "testPlanetTitle", "testPlanetDesc");

        // add test subscription
        testSub = new Subscription();
        testSub.setTitle(externalFeedUrl);
        testSub.setFeedURL(externalFeedUrl);
        testSub.setPlanet(planet);
        planet.getSubscriptions().add(testSub);
        planetManager.savePlanet(planet);
        planetManager.saveSubscription(testSub);
        endSession(true);
    }
    
    @After
    public void tearDown() throws Exception {
        teardownSubscription(testSub.getId());
        teardownPlanet("testPlanetHandle");
        teardownWeblog(testWeblog.getId());
        teardownUser(testUser.getUserName());
    }

    @Test
    public void testFetchFeed() throws WebloggerException {
        try {
            // fetch feed
            Subscription sub = feedManager.fetchSubscription(externalFeedUrl);
            assertNotNull(sub);
            assertEquals(externalFeedUrl, sub.getFeedURL());
            assertEquals(expectedSiteUrl, sub.getSiteURL());
            assertEquals(expectedTitle, sub.getTitle());
            assertNotNull(sub.getLastUpdated());
            assertTrue(sub.getEntries().size() > 0);

        } catch (WebloggerException ex) {
            log.error("Error fetching feed", ex);
            throw ex;
        }
    }

    @Test
    public void testFetchFeedConditionally() throws WebloggerException {
        try {
            // fetch feed
            Subscription sub = feedManager.fetchSubscription(externalFeedUrl);
            assertNotNull(sub);
            assertEquals(externalFeedUrl, sub.getFeedURL());
            assertEquals(expectedSiteUrl, sub.getSiteURL());
            assertEquals(expectedTitle, sub.getTitle());
            assertNotNull(sub.getLastUpdated());
            assertTrue(sub.getEntries().size() > 0);

            // now do a conditional fetch and we should get back null
            Subscription updatedSub = feedManager.fetchSubscription(externalFeedUrl, sub.getLastUpdated());
            assertNull(updatedSub);

        } catch (WebloggerException ex) {
            log.error("Error fetching feed", ex);
            throw ex;
        }
    }

    @Test
    public void testFetchInternalSubscription() throws Exception {
        try {
            // first fetch non-conditionally so we know we should get a Sub
            Subscription sub = feedManager.fetchSubscription(rollerFeedUrl);
            assertNotNull(sub);
            assertEquals(rollerFeedUrl, sub.getFeedURL());
            assertNotNull(sub.getLastUpdated());

            // now do a conditional fetch and we should get back null
            Subscription updatedSub = feedManager.fetchSubscription(rollerFeedUrl, sub.getLastUpdated());
            assertNull(updatedSub);

        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testUpdateSubscription() throws Exception {
        Subscription sub = planetManager.getSubscriptionById(testSub.getId());

        // update the subscription
        Set<Subscription> subscriptionSet = new HashSet<>();
        subscriptionSet.add(sub);
        feedManager.updateSubscriptions(subscriptionSet);
        endSession(true);

        // verify the results
        sub = planetManager.getSubscription(planet, externalFeedUrl);
        assertNotNull(sub);
        assertEquals(externalFeedUrl, sub.getFeedURL());
        assertEquals(expectedSiteUrl, sub.getSiteURL());
        assertEquals(expectedTitle, sub.getTitle());
        assertNotNull(sub.getLastUpdated());
        assertTrue(sub.getEntries().size() > 0);
    }

}