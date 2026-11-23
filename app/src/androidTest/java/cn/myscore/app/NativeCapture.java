package cn.myscore.app;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.Window;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class NativeCapture {
    private NativeCapture() {}

    static Bitmap take(Instrumentation test, Window window) throws IOException {
        Bitmap[] image = new Bitmap[1];
        int[] result = {-1};
        CountDownLatch ready = new CountDownLatch(1);
        test.runOnMainSync(() -> {
            int width = window.getDecorView().getWidth(), height = window.getDecorView().getHeight();
            if (width <= 0 || height <= 0) { ready.countDown(); return; }
            image[0] = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            PixelCopy.request(window, image[0], code -> {
                result[0] = code;
                ready.countDown();
            }, new Handler(Looper.getMainLooper()));
        });
        try {
            if (!ready.await(5, TimeUnit.SECONDS) || result[0] != PixelCopy.SUCCESS) throw new IOException("PixelCopy failed: " + result[0]);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Capture interrupted", error);
        }
        return image[0];
    }
}
