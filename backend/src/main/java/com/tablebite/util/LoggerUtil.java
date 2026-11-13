package com.tablebite.util;

import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Small wrapper around java.util.logging with a compact, readable format.
 */
public final class LoggerUtil {
    private static volatile boolean configured;

    private LoggerUtil() {
    }

    public static Logger get(Class<?> type) {
        configure();
        return Logger.getLogger(type.getName());
    }

    private static synchronized void configure() {
        if (configured) {
            return;
        }
        Logger root = Logger.getLogger("");
        for (Handler handler : root.getHandlers()) {
            root.removeHandler(handler);
        }
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        handler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord record) {
                String source = record.getLoggerName();
                int dot = source.lastIndexOf('.');
                String shortName = dot >= 0 ? source.substring(dot + 1) : source;
                StringBuilder line = new StringBuilder()
                        .append(String.format("%1$tF %1$tT", record.getMillis()))
                        .append(' ').append(String.format("%-7s", record.getLevel().getName()))
                        .append(' ').append(shortName).append(" - ")
                        .append(formatMessage(record)).append(System.lineSeparator());
                if (record.getThrown() != null) {
                    line.append("    ").append(record.getThrown()).append(System.lineSeparator());
                }
                return line.toString();
            }
        });
        root.addHandler(handler);
        root.setLevel(Level.INFO);
        configured = true;
    }
}
