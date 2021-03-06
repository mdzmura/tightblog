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
 */

package org.apache.roller.weblogger.ui.rendering.comment;

import org.apache.roller.weblogger.pojos.WeblogEntryComment;

import java.util.List;
import java.util.Map;

/**
 * Interface for comment validation plugin
 */
public interface CommentValidator {

    /**
     * Plain text name of validator for display purposes.
     */
    String getName();

    /**
     * @param comment  Comment to be validated
     * @param messages Map of resource bundle strings and optional arguments to which errors will added
     * @return Number indicating confidence that comment is valid (100 meaning 100%)
     */
    int validate(WeblogEntryComment comment, Map<String, List<String>> messages);
}
