package dev.budswatch.remote;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import dev.budswatch.R;
import dev.budswatch.protocol.Configs;
import dev.budswatch.ui.MainActivity;

/**
 * Card for the ColorOS Watch card screen (swipe from the watch face). It is a standard AppWidget sized like
 * OPPO's own cards (217 x 88 dp) and also listens to the launcher's "card screen is showing" broadcast so
 * the state is fresh whenever the user looks at it.
 */
public final class AncCardProvider extends AppWidgetProvider {
    static final String ACTION_MODE = "dev.budswatch.action.SET_MODE";
    static final String EXTRA_MODE = "mode";
    private static final String ACTION_CARD_SHOWING = "com.heytap.wearable.launcher.action.quick_center_start_showing";

    @Override public void onReceive(Context ctx, Intent intent) {
        String a = intent.getAction();
        if (ACTION_MODE.equals(a)) {
            BudsRemote.setMode(ctx, intent.getIntExtra(EXTRA_MODE, Configs.ANC_NOISE));
            return;
        }
        if (ACTION_CARD_SHOWING.equals(a)) {
            updateAll(ctx);
            if (hasCards(ctx)) BudsRemote.refresh(ctx);
            return;
        }
        super.onReceive(ctx, intent);
    }

    @Override public void onUpdate(Context ctx, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, build(ctx));
    }

    private static boolean hasCards(Context ctx) {
        int[] ids = AppWidgetManager.getInstance(ctx).getAppWidgetIds(new ComponentName(ctx, AncCardProvider.class));
        return ids != null && ids.length > 0;
    }

    public static void updateAll(Context ctx) {
        AppWidgetManager m = AppWidgetManager.getInstance(ctx);
        int[] ids = m.getAppWidgetIds(new ComponentName(ctx, AncCardProvider.class));
        if (ids == null || ids.length == 0) return;
        RemoteViews v = build(ctx);
        for (int id : ids) m.updateAppWidget(id, v);
    }

    private static RemoteViews build(Context ctx) {
        BudsRemote.Cache c = BudsRemote.Cache.get(ctx);
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.card_anc);

        String name = c.name == null || c.name.isEmpty() ? "耳机" : c.name.replaceFirst("(?i)^(redmi|xiaomi)\\s+", "");
        v.setTextViewText(R.id.card_title, name);
        String status;
        switch (BudsRemote.link()) {
            case BudsRemote.LINK_CONNECTING: status = "连接中…"; break;
            case BudsRemote.LINK_FAILED: status = "未连接"; break;
            default:
                status = c.left < 0 && c.right < 0 ? "点按切换" : "L " + pct(c.left) + "  R " + pct(c.right);
        }
        v.setTextViewText(R.id.card_status, status);

        int[] ids = {R.id.btn_off, R.id.btn_anc, R.id.btn_trans};
        int[] modes = {Configs.ANC_OFF, Configs.ANC_NOISE, Configs.ANC_TRANSPARENT};
        int[] icons = {R.id.ic_off, R.id.ic_anc, R.id.ic_trans};
        int[] labels = {R.id.lb_off, R.id.lb_anc, R.id.lb_trans};
        for (int i = 0; i < 3; i++) {
            boolean on = c.mode == modes[i];
            v.setInt(ids[i], "setBackgroundResource", on ? R.drawable.card_btn_on : R.drawable.card_btn_off);
            v.setInt(icons[i], "setColorFilter", on ? 0xFF000000 : 0xFFFFFFFF);
            v.setTextColor(labels[i], on ? 0xFF000000 : 0xFFDDE3EA);
            Intent it = new Intent(ctx, AncCardProvider.class).setAction(ACTION_MODE).putExtra(EXTRA_MODE, modes[i]);
            v.setOnClickPendingIntent(ids[i], PendingIntent.getBroadcast(ctx, 100 + i, it,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
        Intent open = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        v.setOnClickPendingIntent(R.id.card_header, PendingIntent.getActivity(ctx, 99, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return v;
    }

    private static String pct(int p) { return p < 0 ? "–" : p + "%"; }
}
