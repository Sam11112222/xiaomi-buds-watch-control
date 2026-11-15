package dev.budswatch.remote;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.concurrent.futures.ResolvableFuture;
import androidx.wear.protolayout.ActionBuilders;
import androidx.wear.protolayout.ColorBuilders;
import androidx.wear.protolayout.DimensionBuilders;
import androidx.wear.protolayout.LayoutElementBuilders;
import androidx.wear.protolayout.ModifiersBuilders;
import androidx.wear.protolayout.ResourceBuilders;
import androidx.wear.protolayout.TimelineBuilders;
import androidx.wear.tiles.RequestBuilders;
import androidx.wear.tiles.TileBuilders;
import androidx.wear.tiles.TileService;

import com.google.common.util.concurrent.ListenableFuture;

import dev.budswatch.protocol.Configs;

/**
 * Wear OS Tile (Google Wear OS watches): the same Off / Noise cancelling / Transparency control as the
 * ColorOS card. ColorOS Watch builds without a tile host simply never bind it.
 */
public final class AncTileService extends TileService {
    private static final String RES_VERSION = "1";
    private static final int BG_OFF = 0xFF2A313D, BG_ON = 0xFF5FB2FF, FG = 0xFFFFFFFF, DIM = 0xFF9AA0A8;

    public static void requestRefresh(Context ctx) {
        try {
            TileService.getUpdater(ctx).requestUpdate(AncTileService.class);
        } catch (Throwable ignored) {
            // no tile host on this watch
        }
    }

    @NonNull @Override
    protected ListenableFuture<TileBuilders.Tile> onTileRequest(@NonNull RequestBuilders.TileRequest req) {
        String clicked = req.getCurrentState().getLastClickableId();
        BudsRemote.Cache c = BudsRemote.Cache.get(this);
        if (clicked.startsWith("mode_")) {
            int mode = Integer.parseInt(clicked.substring(5));
            if (mode != c.mode || BudsRemote.link() != BudsRemote.LINK_READY) BudsRemote.setMode(this, mode);
            c = BudsRemote.Cache.get(this);
        } else if (BudsRemote.link() == BudsRemote.LINK_IDLE) {
            BudsRemote.refresh(this);
        }
        TileBuilders.Tile tile = new TileBuilders.Tile.Builder()
                .setResourcesVersion(RES_VERSION)
                .setFreshnessIntervalMillis(0)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout(c)))
                .build();
        ResolvableFuture<TileBuilders.Tile> f = ResolvableFuture.create();
        f.set(tile);
        return f;
    }

    @NonNull @Override
    protected ListenableFuture<ResourceBuilders.Resources> onTileResourcesRequest(
            @NonNull RequestBuilders.ResourcesRequest req) {
        ResolvableFuture<ResourceBuilders.Resources> f = ResolvableFuture.create();
        f.set(new ResourceBuilders.Resources.Builder().setVersion(RES_VERSION).build());
        return f;
    }

    private static LayoutElementBuilders.LayoutElement layout(BudsRemote.Cache c) {
        String name = c.name == null || c.name.isEmpty() ? "耳机" : c.name;
        String status;
        switch (BudsRemote.link()) {
            case BudsRemote.LINK_CONNECTING: status = "连接中…"; break;
            case BudsRemote.LINK_FAILED: status = "未连接"; break;
            default: status = c.left < 0 && c.right < 0 ? "点按切换降噪模式"
                    : "左 " + pct(c.left) + "   右 " + pct(c.right);
        }
        LayoutElementBuilders.Row.Builder row = new LayoutElementBuilders.Row.Builder();
        String[] labels = {"关闭", "降噪", "通透"};
        int[] modes = {Configs.ANC_OFF, Configs.ANC_NOISE, Configs.ANC_TRANSPARENT};
        for (int i = 0; i < 3; i++) {
            if (i > 0) row.addContent(new LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(6)).build());
            row.addContent(button(labels[i], modes[i], c.mode == modes[i]));
        }
        return new LayoutElementBuilders.Column.Builder()
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(text(name, 15, FG, true))
                .addContent(new LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(4)).build())
                .addContent(text(status, 12, DIM, false))
                .addContent(new LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(12)).build())
                .addContent(row.build())
                .build();
    }

    private static LayoutElementBuilders.LayoutElement button(String label, int mode, boolean on) {
        ModifiersBuilders.Modifiers mods = new ModifiersBuilders.Modifiers.Builder()
                .setBackground(new ModifiersBuilders.Background.Builder()
                        .setColor(ColorBuilders.argb(on ? BG_ON : BG_OFF))
                        .setCorner(new ModifiersBuilders.Corner.Builder().setRadius(DimensionBuilders.dp(26)).build())
                        .build())
                .setClickable(new ModifiersBuilders.Clickable.Builder()
                        .setId("mode_" + mode)
                        .setOnClick(new ActionBuilders.LoadAction.Builder().build())
                        .build())
                .build();
        return new LayoutElementBuilders.Box.Builder()
                .setWidth(DimensionBuilders.dp(52))
                .setHeight(DimensionBuilders.dp(52))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(mods)
                .addContent(text(label, 13, on ? 0xFF000000 : FG, true))
                .build();
    }

    private static LayoutElementBuilders.Text text(String s, float sp, int color, boolean bold) {
        LayoutElementBuilders.FontStyle.Builder fs = new LayoutElementBuilders.FontStyle.Builder()
                .setSize(DimensionBuilders.sp(sp))
                .setColor(ColorBuilders.argb(color));
        if (bold) fs.setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD);
        return new LayoutElementBuilders.Text.Builder().setText(s).setFontStyle(fs.build()).setMaxLines(1).build();
    }

    private static String pct(int p) { return p < 0 ? "–" : p + "%"; }
}
