package tool.xfy9326.floatpicture.Utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AppExecutors {
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "floatpicture-io");
        thread.setDaemon(false);
        return thread;
    });

    private AppExecutors() {
    }

    public static ExecutorService io() {
        return IO_EXECUTOR;
    }
}
