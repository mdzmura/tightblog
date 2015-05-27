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
package org.apache.roller.weblogger.business.search.operations;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.business.WeblogEntryManager;
import org.apache.roller.weblogger.business.search.FieldConstants;
import org.apache.roller.weblogger.business.search.IndexManagerImpl;
import org.apache.roller.weblogger.pojos.WeblogEntry;

/**
 * An operation that adds a new log entry into the index.
 * 
 * @author Mindaugas Idzelis (min@idzelis.com)
 */
public class ReIndexEntryOperation extends WriteToIndexOperation {

    // ~ Static fields/initializers
    // =============================================

    private static Log mLogger = LogFactory.getFactory().getInstance(
            AddEntryOperation.class);

    // ~ Instance fields
    // ========================================================

    private WeblogEntry data;
    private WeblogEntryManager weblogEntryManager;

    // ~ Constructors
    // ===========================================================

    /**
     * Adds a web log entry into the index.
     */
    public ReIndexEntryOperation(WeblogEntryManager wem, IndexManagerImpl mgr,
            WeblogEntry data) {
        super(mgr);
        this.weblogEntryManager = wem;
        this.data = data;
    }

    // ~ Methods
    // ================================================================

    public void doRun() {

        // since this operation can be run on a separate thread we must treat
        // the weblog object passed in as a detached object which is prone to
        // lazy initialization problems, so requery for the object now
        try {
            this.data = weblogEntryManager.getWeblogEntry(this.data.getId());
        } catch (WebloggerException ex) {
            mLogger.error("Error getting weblogentry object", ex);
            return;
        }

        IndexWriter writer = beginWriting();
        try {
            if (writer != null) {

                // Delete Doc
                Term term = new Term(FieldConstants.ID, data.getId());
                writer.deleteDocuments(term);

                // Add Doc
                writer.addDocument(getDocument(data));
            }
        } catch (IOException e) {
            mLogger.error("Problems adding/deleting doc to index", e);
        } finally {
            endWriting();
        }
    }
}
