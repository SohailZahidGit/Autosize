
package com.tang.autodensity;

import android.app.Activity;
import android.app.Dialog;
import android.app.Fragment;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.view.View;

import com.tang.autodensity.external.ExternalAdapterInfo;
import com.tang.autodensity.internal.CancelAdapter;
import com.tang.autodensity.internal.CustomAdapter;
import com.tang.autodensity.utils.Preconditions;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ================================================
 * AndroidAutoSize 用于屏幕适配的核心方法都在这里, 核心原理来自于 <a href="https://mp.weixin.qq.com/s/d9QCoBP6kV9VSWvVldVVwA">今日头条官方适配方案</a>
 * 此方案只要应用到 {@link Activity} 上, 这个 {@link Activity} 下的所有 {@link Fragment}、{@link Dialog}、
 * 自定义 {@link View} 都会达到适配的效果, 如果某个页面不想使用适配请让该 {@link Activity} 实现 {@link CancelAdapter}
 * <p>
 * 任何方案都不可能完美, 在成本和收益中做出取舍, 选择出最适合自己的方案即可, 在没有更好的方案出来之前, 只有继续忍耐它的不完美, 或者自己作出改变
 * 既然选择, 就不要抱怨, 感谢 今日头条技术团队 和 张鸿洋 等人对 Android 屏幕适配领域的的贡献
 * <p>
 * ================================================
 */
public final class AutoSize {
    private static Map<String, DisplayMetricsInfo> mCache = new ConcurrentHashMap<>();

    private AutoSize() {
        throw new IllegalStateException("you can't instantiate me!");
    }

    /**
     * 使用 AndroidAutoSize 初始化时设置的默认适配参数进行适配 (AndroidManifest 的 Meta 属性)
     *
     * @param activity {@link Activity}
     */
    public static void autoConvertDensityOfGlobal(Activity activity) {
        if (DensityConfig.getInstance().isBaseOnWidth()) {
            autoConvertDensityBaseOnWidth(activity, DensityConfig.getInstance().getDesignWidthInDp());
        } else {
            autoConvertDensityBaseOnHeight(activity, DensityConfig.getInstance().getDesignHeightInDp());
        }
    }

    /**
     * 使用 {@link Activity} 的自定义参数进行适配
     *
     * @param activity    {@link Activity}
     * @param customAdapt {@link Activity} 需实现 {@link com.tang.autodensity.internal.CustomAdapter}
     */
    public static void autoConvertDensityOfCustomAdapt(Activity activity, CustomAdapter customAdapt) {
        Preconditions.checkNotNull(customAdapt, "customAdapt == null");
        float sizeInDp = customAdapt.getSizeInDp();

        //如果 CustomAdapt#getSizeInDp() 返回 0, 则使用在 AndroidManifest 上填写的设计图尺寸
        if (sizeInDp <= 0) {
            if (customAdapt.isBaseOnWidth()) {
                sizeInDp = DensityConfig.getInstance().getDesignWidthInDp();
            } else {
                sizeInDp = DensityConfig.getInstance().getDesignHeightInDp();
            }
        }
        autoConvertDensity(activity, sizeInDp, customAdapt.isBaseOnWidth());
    }

    /**
     * 使用外部三方库的 {@link Activity} 的自定义适配参数进行适配
     *
     * @param activity          {@link Activity}
     * @param externalAdaptInfo 三方库的 {@link Activity} 提供的适配参数
     */
    public static void autoConvertDensityOfExternalAdaptInfo(Activity activity, ExternalAdapterInfo externalAdaptInfo) {
        Preconditions.checkNotNull(externalAdaptInfo, "externalAdaptInfo == null");
        float sizeInDp = externalAdaptInfo.getSizeInDp();

        //如果 ExternalAdaptInfo#getSizeInDp() 返回 0, 则使用在 AndroidManifest 上填写的设计图尺寸
        if (sizeInDp <= 0) {
            if (externalAdaptInfo.isBaseOnWidth()) {
                sizeInDp = DensityConfig.getInstance().getDesignWidthInDp();
            } else {
                sizeInDp = DensityConfig.getInstance().getDesignHeightInDp();
            }
        }
        autoConvertDensity(activity, sizeInDp, externalAdaptInfo.isBaseOnWidth());
    }

    /**
     * 以宽度为基准进行适配
     *
     * @param activity        {@link Activity}
     * @param designWidthInDp 设计图的总宽度
     */
    public static void autoConvertDensityBaseOnWidth(Activity activity, float designWidthInDp) {
        autoConvertDensity(activity, designWidthInDp, true);
    }

    /**
     * 以高度为基准进行适配
     *
     * @param activity         {@link Activity}
     * @param designHeightInDp 设计图的总高度
     */
    public static void autoConvertDensityBaseOnHeight(Activity activity, float designHeightInDp) {
        autoConvertDensity(activity, designHeightInDp, false);
    }

    /**
     * 这里是今日头条适配方案的核心代码, 核心在于根据当前设备的实际情况做自动计算并转换 {@link DisplayMetrics#density}、
     * {@link DisplayMetrics#scaledDensity}、{@link DisplayMetrics#densityDpi} 这三个值, 有兴趣请看下面的链接
     *
     * @param activity      {@link Activity}
     * @param sizeInDp      设计图上的设计尺寸, 单位 dp, 如果 {@param isBaseOnWidth} 设置为 {@code true},
     *                      {@param sizeInDp} 则应该填写设计图的总宽度, 如果 {@param isBaseOnWidth} 设置为 {@code false},
     *                      {@param sizeInDp} 则应该填写设计图的总高度
     * @param isBaseOnWidth 是否按照宽度进行等比例适配, {@code true} 为以宽度进行等比例适配, {@code false} 为以高度进行等比例适配
     * @see <a href="https://mp.weixin.qq.com/s/d9QCoBP6kV9VSWvVldVVwA">今日头条官方适配方案</a>
     */
    public static void autoConvertDensity(Activity activity, float sizeInDp, boolean isBaseOnWidth) {
        Preconditions.checkNotNull(activity, "activity == null");

        int screenSize = isBaseOnWidth ? DensityConfig.getInstance().getScreenWidth()
                : DensityConfig.getInstance().getScreenHeight();
        String key = sizeInDp + "|" + isBaseOnWidth + "|"
                + DensityConfig.getInstance().isUseDeviceSize() + "|"
                + DensityConfig.getInstance().getInitScaledDensity() + "|"
                + screenSize;

        DisplayMetricsInfo displayMetricsInfo = mCache.get(key);

        float targetDensity = 0;
        int targetDensityDpi = 0;
        float targetScaledDensity = 0;

        if (displayMetricsInfo == null) {
            if (isBaseOnWidth) {
                targetDensity = DensityConfig.getInstance().getScreenWidth() * 1.0f / sizeInDp;
            } else {
                targetDensity = DensityConfig.getInstance().getScreenHeight() * 1.0f / sizeInDp;
            }
            targetScaledDensity = targetDensity * (DensityConfig.getInstance().
                    getInitScaledDensity() * 1.0f / DensityConfig.getInstance().getInitDensity());
            targetDensityDpi = (int) (targetDensity * 160);

            mCache.put(key, new DisplayMetricsInfo(targetDensity, targetDensityDpi, targetScaledDensity));
        } else {
            targetDensity = displayMetricsInfo.density;
            targetDensityDpi = displayMetricsInfo.densityDpi;
            targetScaledDensity = displayMetricsInfo.scaledDensity;
        }

        setDensity(activity, targetDensity, targetDensityDpi, targetScaledDensity);
    }

    /**
     * 取消适配
     *
     * @param activity {@link Activity}
     */
    public static void cancelAdapt(Activity activity) {
        setDensity(activity, DensityConfig.getInstance().getInitDensity()
                , DensityConfig.getInstance().getInitDensityDpi()
                , DensityConfig.getInstance().getInitScaledDensity());
    }

    /**
     * 给几大 {@link DisplayMetrics} 赋值
     *
     * @param activity      {@link Activity}
     * @param density       {@link DisplayMetrics#density}
     * @param densityDpi    {@link DisplayMetrics#densityDpi}
     * @param scaledDensity {@link DisplayMetrics#scaledDensity}
     */
    private static void setDensity(Activity activity, float density, int densityDpi, float scaledDensity) {
        final DisplayMetrics activityDisplayMetrics = activity.getResources().getDisplayMetrics();
        final DisplayMetrics appDisplayMetrics = DensityConfig.getInstance().getApplication().getResources().getDisplayMetrics();

        setDensity(activityDisplayMetrics, density, densityDpi, scaledDensity);

        setDensity(appDisplayMetrics, density, densityDpi, scaledDensity);

        //兼容 MIUI
        DisplayMetrics activityDisplayMetricsOnMIUI = getMetricsOnMiui(activity.getResources());
        DisplayMetrics appDisplayMetricsOnMIUI = getMetricsOnMiui(DensityConfig.getInstance().getApplication().getResources());

        if (activityDisplayMetricsOnMIUI != null) {
            setDensity(activityDisplayMetricsOnMIUI, density, densityDpi, scaledDensity);
        }

        if (appDisplayMetricsOnMIUI != null) {
            setDensity(appDisplayMetricsOnMIUI, density, densityDpi, scaledDensity);
        }
    }

    /**
     * 赋值
     *
     * @param displayMetrics {@link DisplayMetrics}
     * @param density        {@link DisplayMetrics#density}
     * @param densityDpi     {@link DisplayMetrics#densityDpi}
     * @param scaledDensity  {@link DisplayMetrics#scaledDensity}
     */
    private static void setDensity(DisplayMetrics displayMetrics, float density, int densityDpi, float scaledDensity) {
        displayMetrics.density = density;
        displayMetrics.densityDpi = densityDpi;
        displayMetrics.scaledDensity = scaledDensity;
    }

    /**
     * 解决 MIUI 更改框架导致的 MIUI7 + Android5.1.1 上出现的失效问题 (以及极少数基于这部分 MIUI 去掉 ART 然后置入 XPosed 的手机)
     * 来源于: https://github.com/Firedamp/Rudeness/blob/master/rudeness-sdk/src/main/java/com/bulong/rudeness/RudenessScreenHelper.java#L61:5
     *
     * @param resources {@link Resources}
     * @return {@link DisplayMetrics}, 可能为 {@code null}
     */
    private static DisplayMetrics getMetricsOnMiui(Resources resources) {
        if ("MiuiResources".equals(resources.getClass().getSimpleName()) || "XResources".equals(resources.getClass().getSimpleName())) {
            try {
                Field field = Resources.class.getDeclaredField("mTmpMetrics");
                field.setAccessible(true);
                return (DisplayMetrics) field.get(resources);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
