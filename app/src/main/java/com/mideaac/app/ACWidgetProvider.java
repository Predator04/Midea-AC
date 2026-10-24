package com.mideaac.app;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

public class ACWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_TOGGLE = "com.mideaac.app.TOGGLE";
    public static final String ACTION_REFRESH = "com.mideaac.app.REFRESH";

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) WidgetUpdateService.update(ctx, id, false);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        String a = intent.getAction();
        if (ACTION_TOGGLE.equals(a) || ACTION_REFRESH.equals(a)) {
            int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
            if (id != -1) WidgetUpdateService.update(ctx, id, ACTION_TOGGLE.equals(a));
        }
    }

    static void updateAll(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, ACWidgetProvider.class));
        for (int id : ids) WidgetUpdateService.update(ctx, id, false);
    }
}
