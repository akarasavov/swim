package atk.app.util;

public class ExceptionUtil {

    public static void ignoreThrownExceptions(Callable callable) {
        try {
            callable.call();
        } catch (Exception ignored) {
        }
    }

    public static interface Callable {
        void call() throws Exception;
    }
}
