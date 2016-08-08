package sensors.ibluez;

import android.app.Application;
import android.content.Context;

/**
 * Created by cost on 8/5/16.
 */
public class MyApplication extends Application {

    private static Context context;

    public void onCreate() {
        super.onCreate();
        MyApplication.context = getApplicationContext();
    }

    public static void setContext(Context mContext) {
        context = mContext;
    }

    public static Context getAppContext() {
        return MyApplication.context;
    }
}