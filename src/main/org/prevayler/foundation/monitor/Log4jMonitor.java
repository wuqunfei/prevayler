// Prevayler, The Free-Software Prevalence Layer
// Copyright 2001-2006 by Klaus Wuestefeld
//
// This library is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE.
//
// Prevayler is a trademark of Klaus Wuestefeld.
// See the LICENSE file for license details.

package org.prevayler.foundation.monitor;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * A Monitor that logs output using Log4j. Loggers are named by fully qualified
 * class name for easy configuration and control over logging output.
 */
public class Log4jMonitor extends LoggingMonitor {

    private static final String callerFQCN = LoggingMonitor.class.getName();

    @Override protected void info(Class clazz, String message) {
        log(clazz, Level.INFO, message, null);
    }

    @Override protected void error(Class clazz, String message, Exception ex) {
        log(clazz, Level.ERROR, message, ex);
    }

    @Override protected boolean isInfoEnabled(Class clazz) {
        return logger(clazz).isInfoEnabled();
    }

    private Logger logger(Class clazz) {
        return Logger.getLogger(clazz);
    }

    private void log(Class clazz, Level level, String message, Exception ex) {
        logger(clazz).log(callerFQCN, level, message, ex);
    }
}
