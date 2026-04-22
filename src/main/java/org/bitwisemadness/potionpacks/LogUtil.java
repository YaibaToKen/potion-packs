package org.bitwisemadness.potionpacks;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class LogUtil {
	private static final Logger LOGGER = LogManager.getLogger(PotionPacksMod.MOD_ID);

	private LogUtil() {}

	public static void info(Env env, String message, Object... args) {
		LOGGER.info(prefix(env) + message, args);
	}

	public static void warn(Env env, String message, Object... args) {
		LOGGER.warn(prefix(env) + message, args);
	}

	public static void error(Env env, String message, Object... args) {
		LOGGER.error(prefix(env) + message, args);
	}

	public static void debug(Env env, String message, Object... args) {
		LOGGER.debug(prefix(env) + message, args);
	}

	private static String prefix(Env env) {
		return "[PotionPacks-" + env.name() + "] ";
	}
}
