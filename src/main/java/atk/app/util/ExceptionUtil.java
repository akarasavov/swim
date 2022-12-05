package atk.app.util;

import org.slf4j.Logger;

public class ExceptionUtil {

    public static void ignoreThrownExceptions(Callable callable, Logger logger) {
        try {
            callable.call();
        } catch (Exception exception) {
            logger.warn("{}", exception.toString());
        }
    }

    public static interface Callable {
        void call() throws Exception;
    }
}
