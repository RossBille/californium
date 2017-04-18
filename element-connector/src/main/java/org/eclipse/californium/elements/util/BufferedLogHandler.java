/*******************************************************************************
 * Copyright (c) 2017 Bosch Software Innovations GmbH and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 * 
 * Contributors:
 *    Bosch Software Innovations GmbH - initial implementation
 ******************************************************************************/
package org.eclipse.californium.elements.util;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

/**
 * Buffered logging handler.
 * 
 * Formats logging messages and stores the messages in a buffer to write them
 * asynchronous to the target handler.
 * 
 * Supported logging properties:
 * 
 * <code>
 * org.eclipse.californium.elements.util.BufferedLogHandler.target:
 *      class name of target handler. Default ConsoleHandler.
 * 
 * org.eclipse.californium.elements.util.BufferedLogHandler.formatter:
 *      formatter of this handler. Default is the formatter of the target handler.
 *      The formatter of the target handler is then replaced by the 
 *      BufferedOutputFormatter.
 *      Note: the formatter of this handler is used for the first level formatting
 *            and the result is stored in {@link LogRecord#setMessage(String)}.
 *            Therefore the formatter of the target handler should usually be
 *            BufferedOutputFormatter.
 *            
 * org.eclipse.californium.elements.util.BufferedLogHandler.level:
 *      Level of handler. Default is the level of the target handler.
 * 
 * org.eclipse.californium.elements.util.BufferedLogHandler.closingtimeout:
 *      Timeout in milliseconds to wait when closing the handler, until the
 *      last messages are written. Default 0 (wait for ever).
 *      Note: a value bigger then 0 start the logging thread as daemon.
 * 
 * org.eclipse.californium.elements.util.BufferedLogHandler.warningdelaythreshold:
 *      Threshold in milliseconds for the logging delay, when a warning is written.
 *      0 := disable warning. Default 500ms.
 *      
 * </code>
 */
public class BufferedLogHandler extends Handler {

	private static final String PROPERTY_NAME_TARGET = "target";
	private static final String PROPERTY_NAME_FORMATTER = "formatter";
	private static final String PROPERTY_NAME_CLOSING_TIMEOUT = "closingtimeout";
	private static final String PROPERTY_NAME_WARNING_DELAY_THRESHOLD = "warningdelaythreshold";
	private static final String PROPERTY_NAME_LEVEL = "level";
	private static final long DEFAULT_WARNING_DELAY_THRESHOLD_IN_MS = 500;
	private static final long DEFAULT_CLOSING_TIMEOUT_IN_MS = 0;

	private final Thread thread;
	private final Handler target;
	private final Formatter formatter;
	private final BlockingQueue<LogRecord> buffer = new LinkedBlockingQueue<LogRecord>();
	private final long closingTimeoutInMs;
	private final long warningDelayThresholdInMs;
	private AtomicBoolean isClosed = new AtomicBoolean();

	public BufferedLogHandler() {
		super();
		Handler targetHandler = (Handler) newInstance(Handler.class, PROPERTY_NAME_TARGET);
		if (null == targetHandler) {
			targetHandler = new ConsoleHandler();
		}
		this.target = targetHandler;
		Formatter formatter = (Formatter) newInstance(Formatter.class, PROPERTY_NAME_FORMATTER);
		if (null == formatter) {
			formatter = targetHandler.getFormatter();
			targetHandler.setFormatter(new BufferedOutputFormatter());
		}
		this.formatter = formatter;
		this.closingTimeoutInMs = getLong(PROPERTY_NAME_CLOSING_TIMEOUT, DEFAULT_CLOSING_TIMEOUT_IN_MS);
		this.warningDelayThresholdInMs = getLong(PROPERTY_NAME_WARNING_DELAY_THRESHOLD,
				DEFAULT_WARNING_DELAY_THRESHOLD_IN_MS);

		Level level;
		String levelName = getValue(PROPERTY_NAME_LEVEL);
		if (null != levelName) {
			level = Level.parse(levelName);
		} else {
			level = target.getLevel();
		}
		setLevel(level);
		setFilter(target.getFilter());
		target.setFilter(null);

		LogRecord record = new LogRecord(Level.FINE, "BufferedLogHandler starting ...");
		record.setSourceMethodName("<init>");
		publishTarget(record);

		thread = new Thread("LOG-PUB") {

			@Override
			public void run() {
				String closingMessage = "BufferedLogHandler closed.";
				while (!isClosed.get() || !buffer.isEmpty()) {
					try {
						LogRecord record = buffer.take();
						if (0 < warningDelayThresholdInMs) {
							long timeInMs = System.currentTimeMillis();
							long delayInMs = timeInMs - record.getMillis();
							if (warningDelayThresholdInMs < delayInMs) {
								String recordMessage = "D" + delayInMs + "ms " + record.getMessage();
								record.setMessage(recordMessage);
							}
							target.publish(record);
							delayInMs = System.currentTimeMillis() - timeInMs;
							if (warningDelayThresholdInMs < delayInMs) {
								LogRecord warning = new LogRecord(Level.WARNING, "Log delayed! " + delayInMs + " ms");
								warning.setSourceMethodName("out");
								publishTarget(warning);
							}
						} else {
							target.publish(record);
						}
					} catch (InterruptedException e) {
						closingMessage = "BufferedLogHandler closed by interrupt.";
					}
				}
				LogRecord record = new LogRecord(Level.FINE, closingMessage);
				record.setSourceMethodName("close");
				publishTarget(record);
				target.close();
			}
		};
		if (0 < closingTimeoutInMs) {
			thread.setDaemon(true);
		}
		thread.start();
	}

	/**
	 * Get logging property value.
	 * 
	 * @param subProperty sub-property name. Appended to "full.class.name." to
	 *            build the property name.
	 * @return trimmed value of logging property, or null, if not available or
	 *         empty.
	 */
	private String getValue(String subProperty) {
		String value = LogManager.getLogManager().getProperty(BufferedLogHandler.class.getName() + "." + subProperty);
		if (null != value) {
			value = value.trim();
			if (value.isEmpty()) {
				value = null;
			}
		}
		return value;
	}

	private long getLong(String subProperty, long defaultValue) {
		String value = getValue(subProperty);
		if (null != value) {
			return Long.parseLong(value);
		}
		return defaultValue;
	}

	/**
	 * Create a new instance of class from a logging property value.
	 * 
	 * @param type (super-) type of instance
	 * @param subProperty sub-property name. Appended to "<class>.<name>." to
	 *            build the property name.
	 * @return new instance, or null, if not configured in logging properties.
	 */
	private Object newInstance(Class<?> type, String subProperty) {
		String className = getValue(subProperty);
		if (className != null) {
			try {
				Class<?> clz = ClassLoader.getSystemClassLoader().loadClass(className);
				if (type.isAssignableFrom(clz)) {
					return clz.newInstance();
				} else {
					throw new RuntimeException("BufferedHandler \"" + className + "\" is no " + type.getName());
				}
			} catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
				throw new RuntimeException("BufferedHandler can't load \"" + className + "\"", e);
			}
		}
		return null;
	}

	private void publishTarget(LogRecord record) {
		record.setSourceClassName(BufferedLogHandler.class.getName());
		record.setMessage(formatter.format(record));
		target.publish(record);
	}

	@Override
	public void publish(LogRecord record) {
		if (!isClosed.get() && isLoggable(record)) {
			record.setMessage(formatter.format(record));
			buffer.offer(record);
		}
	}

	@Override
	public void flush() {

	}

	/**
	 * {@inheritDoc}
	 * 
	 * Joins the logging thread using {@link #closingTimeoutInMs}.
	 */
	@Override
	public void close() throws SecurityException {
		if (isClosed.compareAndSet(false, true)) {
			thread.interrupt();
		}
		try {
			thread.join(closingTimeoutInMs);
		} catch (InterruptedException e) {
		}
	}
}
