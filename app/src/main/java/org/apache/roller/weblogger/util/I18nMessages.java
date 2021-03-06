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
package org.apache.roller.weblogger.util;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A utility class for handling i18n messaging.
 */
public final class I18nMessages {

    private static Logger log = LoggerFactory.getLogger(I18nMessages.class);

    // locale and bundle we are using for messaging
    private final Locale locale;
    private final ResourceBundle bundle;

    // a map of cached messages instances, keyed by locale
    private static Map<Locale, I18nMessages> messagesMap = Collections.synchronizedMap(new HashMap<>());

    private I18nMessages(Locale locale) {
        this.locale = locale;
        this.bundle = ResourceBundle.getBundle("ApplicationResources", locale);
    }

    /**
     * Get an instance for a given locale.
     */
    public static I18nMessages getMessages(Locale locale) {

        log.debug("request for messages in locale = {}", locale.toString());

        // check if we already have a message utils created for that locale
        I18nMessages messages = messagesMap.get(locale);

        // if no utils for that language yet then construct
        if (messages == null) {
            messages = new I18nMessages(locale);

            // keep a reference to it
            messagesMap.put(messages.getLocale(), messages);
        }

        return messages;
    }

    /**
     * The locale representing this message utils.
     */
    public Locale getLocale() {
        return this.locale;
    }

    /**
     * Get a message from the bundle.
     */
    public String getString(String key) {

        try {
            return bundle.getString(key);
        } catch (Exception e) {
            // send a warning in the logs
            log.warn("Error getting key {}", key);
            return key;
        }
    }

    /**
     * Get a message from the bundle that has just a single parameter.
     */
    public String getString(String key, String param) {

        try {
            String msg = bundle.getString(key);
            return MessageFormat.format(msg, Collections.singletonList(param));
        } catch (Exception e) {
            // send a warning in the logs
            log.warn("Error getting key {}", key, e);
            return key;
        }
    }

    /**
     * Get a message from the bundle and substitute the given args into
     * the message contents.
     */
    public String getString(String key, Object[] args) {

        try {
            String msg = bundle.getString(key);
            return MessageFormat.format(msg, args);
        } catch (Exception e) {
            // send a warning in the logs
            log.warn("Error getting key {}", key, e);
            return key;
        }
    }

}
