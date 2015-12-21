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
package org.apache.roller.weblogger.business.plugins.entry;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.pojos.WeblogEntry;

import javax.annotation.PostConstruct;

/**
 * Temporary Test plugin for debugging...
 */
public class TestPlugin implements WeblogEntryPlugin {

    protected String name = "Test Plugin";

    protected String description = "Does Something";

    public TestPlugin() {
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return StringEscapeUtils.escapeEcmaScript(description);
    }

    @PostConstruct
    public void init() throws WebloggerException {}

    public String render(WeblogEntry entry, String str) {
        return str;
    }
}