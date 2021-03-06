/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.support.v7ox.widget;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Drawable.ConstantState;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.support.annotationox.DrawableRes;
import android.support.annotationox.NonNull;
import android.support.annotationox.Nullable;
//import android.support.graphics.drawable.AnimatedVectorDrawableCompat;
//import android.support.graphics.drawable.VectorDrawableCompat;
import android.support.v4ox.content.ContextCompat;
import android.support.v4ox.graphics.ColorUtils;
import android.support.v4ox.graphics.drawable.DrawableCompat;
import android.support.v4ox.util.ArrayMap;
import android.support.v4ox.util.LongSparseArray;
import android.support.v4ox.util.LruCache;
//import android.support.v7ox.appcompat.R;
import com.karumi.dexterox.R;

import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.util.Xml;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

import static android.support.v7ox.widget.ThemeUtils.getThemeAttrColor;

/**
 * @hide
 */
public final class AppCompatDrawableManager {

    private interface InflateDelegate {
        Drawable createFromXmlInner(@NonNull Context context, @NonNull XmlPullParser parser,
                @NonNull AttributeSet attrs, @Nullable Resources.Theme theme);
    }

    private static final String TAG = " dsa";
    private static final boolean DEBUG = false;
    private static final PorterDuff.Mode DEFAULT_MODE = PorterDuff.Mode.SRC_IN;
    private static final String SKIP_DRAWABLE_TAG = "appcompat_skip_skip";

    private static final String PLATFORM_VD_CLAZZ = "android.graphics.drawable.VectorDrawable";

    private static AppCompatDrawableManager INSTANCE;

    public static AppCompatDrawableManager get() {
        if (INSTANCE == null) {
            INSTANCE = new AppCompatDrawableManager();
            installDefaultInflateDelegates(INSTANCE);
        }
        return INSTANCE;
    }

    private static void installDefaultInflateDelegates(@NonNull AppCompatDrawableManager manager) {
        final int sdk = Build.VERSION.SDK_INT;
        if (sdk < 21) {
            // We only want to use the automatic VectorDrawableCompat handling where it's
            // needed: on devices running before Lollipop
            manager.addDelegate("vector", new VdcInflateDelegate());

            if (sdk >= 11) {
                // AnimatedVectorDrawableCompat only works on API v11+
                manager.addDelegate("animated-vector", new AvdcInflateDelegate());
            }
        }
    }

    private static final ColorFilterLruCache COLOR_FILTER_CACHE = new ColorFilterLruCache(6);

    /**
     * Drawables which should be tinted with the value of {@code R.attr.colorControlNormal},
     * using the default mode using a raw color filter.
     */
    private static final int[] COLORFILTER_TINT_COLOR_CONTROL_NORMAL = {
            R.drawable.abc_text_cursor_material_ox
    };

    /**
     * Drawables which should be tinted with the value of {@code R.attr.colorControlNormal}, using
     * {@link DrawableCompat}'s tinting functionality.
     */
    private static final int[] TINT_COLOR_CONTROL_NORMAL = {
            R.drawable.abc_ic_ab_back_mtrl_am_alpha_ox,

            R.drawable.abc_ic_search_api_mtrl_alpha_ox,

            R.drawable.abc_ic_clear_mtrl_alpha_ox,
            R.drawable.abc_ic_menu_share_mtrl_alpha_ox,
            R.drawable.abc_ic_menu_copy_mtrl_am_alpha_ox,
            R.drawable.abc_ic_menu_cut_mtrl_alpha_ox,
            R.drawable.abc_ic_menu_selectall_mtrl_alpha_ox,
            R.drawable.abc_ic_menu_paste_mtrl_am_alpha_ox,
            R.drawable.abc_ic_menu_moreoverflow_mtrl_alpha_ox,
            R.drawable.abc_ic_voice_search_api_mtrl_alpha_ox
    };

    /**
     * Drawables which should be tinted with the value of {@code R.attr.colorControlActivated},
     * using a color filter.
     */
    private static final int[] COLORFILTER_COLOR_CONTROL_ACTIVATED = {

            R.drawable.abc_text_cursor_material_ox
    };

    /**
     * Drawables which should be tinted with the value of {@code android.R.attr.colorBackground},
     * using the {@link android.graphics.PorterDuff.Mode#MULTIPLY} mode and a color filter.
     */
    private static final int[] COLORFILTER_COLOR_BACKGROUND_MULTIPLY = {

            R.drawable.abc_cab_background_internal_bg_ox

    };

    /**
     * Drawables which should be tinted using a state list containing values of
     * {@code R.attr.colorControlNormal} and {@code R.attr.colorControlActivated}
     */
    private static final int[] TINT_COLOR_CONTROL_STATE_LIST = {
            R.drawable.abc_edit_text_material_ox,
            R.drawable.abc_tab_indicator_material_ox,
            R.drawable.abc_textfield_search_material_ox,
            R.drawable.abc_spinner_mtrl_am_alpha_ox,
            R.drawable.abc_spinner_textfield_background_material_ox,
            R.drawable.abc_ratingbar_full_material_ox,
            R.drawable.abc_switch_track_mtrl_alpha_ox,
            R.drawable.abc_switch_thumb_material_ox,
            R.drawable.abc_btn_default_mtrl_shape_ox,
            R.drawable.abc_btn_borderless_material_ox
    };

    /**
     * Drawables which should be tinted using a state list containing values of
     * {@code R.attr.colorControlNormal} and {@code R.attr.colorControlActivated} for the checked
     * state.
     */
    private static final int[] TINT_CHECKABLE_BUTTON_LIST = {
            R.drawable.abc_btn_check_material_ox,
            R.drawable.abc_btn_radio_material_ox
    };

    private WeakHashMap<Context, SparseArray<ColorStateList>> mTintLists;
    private ArrayMap<String, InflateDelegate> mDelegates;
    private SparseArray<String> mKnownDrawableIdTags;

    private final Object mDelegateDrawableCacheLock = new Object();
    private final WeakHashMap<Context, LongSparseArray<WeakReference<Drawable.ConstantState>>>
            mDelegateDrawableCaches = new WeakHashMap<>(0);

    private TypedValue mTypedValue;

    private boolean mHasCheckedVectorDrawableSetup;

    public Drawable getDrawable(@NonNull Context context, @DrawableRes int resId) {
        return getDrawable(context, resId, false);
    }

    public Drawable getDrawable(@NonNull Context context, @DrawableRes int resId,
            boolean failIfNotKnown) {
        Drawable drawable = loadDrawableFromDelegates(context, resId);
        if (drawable == null) {
            drawable = ContextCompat.getDrawable(context, resId);
        }
        if (drawable != null) {
            // Tint it if needed
            drawable = tintDrawable(context, resId, failIfNotKnown, drawable);
        }
        if (drawable != null) {
            // See if we need to 'fix' the drawable
            DrawableUtils.fixDrawable(drawable);
        }
        return drawable;
    }

    private Drawable tintDrawable(@NonNull Context context, @DrawableRes int resId,
            boolean failIfNotKnown, @NonNull Drawable drawable) {
        final ColorStateList tintList = getTintList(context, resId);
        if (tintList != null) {
            // First mutate the Drawable, then wrap it and set the tint list
            if (DrawableUtils.canSafelyMutateDrawable(drawable)) {
                drawable = drawable.mutate();
            }
            drawable = DrawableCompat.wrap(drawable);
            DrawableCompat.setTintList(drawable, tintList);

            // If there is a blending mode specified for the drawable, use it
            final PorterDuff.Mode tintMode = getTintMode(resId);
            if (tintMode != null) {
                DrawableCompat.setTintMode(drawable, tintMode);
            }
        } else if (resId == R.drawable.abc_cab_background_top_material_ox) {
            return new LayerDrawable(new Drawable[]{
                    getDrawable(context, R.drawable.abc_cab_background_internal_bg_ox),
                    getDrawable(context, R.drawable.abc_cab_background_internal_bg_ox)
            });
        } else if (resId == R.drawable.abc_seekbar_track_material_ox) {
            LayerDrawable ld = (LayerDrawable) drawable;
            setPorterDuffColorFilter(ld.findDrawableByLayerId(android.R.id.background),
                    ThemeUtils.getThemeAttrColor(context, R.attr.colorControlNormal_ox), DEFAULT_MODE);
            setPorterDuffColorFilter(ld.findDrawableByLayerId(android.R.id.secondaryProgress),
                    ThemeUtils.getThemeAttrColor(context, R.attr.colorControlNormal_ox), DEFAULT_MODE);
            setPorterDuffColorFilter(ld.findDrawableByLayerId(android.R.id.progress),
                    ThemeUtils.getThemeAttrColor(context, R.attr.colorControlActivated_ox), DEFAULT_MODE);
        } else if (resId == R.drawable.abc_ratingbar_indicator_material_ox
                || resId == R.drawable.abc_ratingbar_small_material_ox) {
            LayerDrawable ld = (LayerDrawable) drawable;
            setPorterDuffColorFilter(ld.findDrawableByLayerId(android.R.id.background),
                    ThemeUtils.getDisabledThemeAttrColor(context, R.attr.colorControlNormal_ox),
                    DEFAULT_MODE);
            setPorterDuffColorFilter(ld.findDrawableByLayerId(android.R.id.secondaryProgress),
                    ThemeUtils.getThemeAttrColor(context, R.attr.colorControlActivated_ox), DEFAULT_MODE);
            setPorterDuffColorFilter(ld.findDrawableByLayerId(android.R.id.progress),
                    ThemeUtils.getThemeAttrColor(context, R.attr.colorControlActivated_ox), DEFAULT_MODE);
        } else {
            final boolean tinted = tintDrawableUsingColorFilter(context, resId, drawable);
            if (!tinted && failIfNotKnown) {
                // If we didn't tint using a ColorFilter, and we're set to fail if we don't
                // know the id, return null
                drawable = null;
            }
        }
        return drawable;
    }

    private Drawable loadDrawableFromDelegates(@NonNull Context context, @DrawableRes int resId) {
        if (mDelegates != null && !mDelegates.isEmpty()) {
            if (mKnownDrawableIdTags != null) {
                final String cachedTagName = mKnownDrawableIdTags.get(resId);
                if (SKIP_DRAWABLE_TAG.equals(cachedTagName)
                        || (cachedTagName != null && mDelegates.get(cachedTagName) == null)) {
                    // If we don't have a delegate for the drawable tag, or we've been set to
                    // skip it, fail fast and return null
                    if (DEBUG) {
                        Log.d(TAG, "[loadDrawableFromDelegates] Skipping drawable: "
                                + context.getResources().getResourceName(resId));
                    }
                    return null;
                }
            } else {
                // Create an id cache as we'll need one later
                mKnownDrawableIdTags = new SparseArray<>();
            }

            if (mTypedValue == null) {
                mTypedValue = new TypedValue();
            }

            final TypedValue tv = mTypedValue;
            final Resources res = context.getResources();
            res.getValue(resId, tv, true);

            final long key = (((long) tv.assetCookie) << 32) | tv.data;

            Drawable dr = getCachedDelegateDrawable(context, key);
            if (dr != null) {
                if (DEBUG) {
                    Log.i(TAG, "[loadDrawableFromDelegates] Returning cached drawable: " +
                            context.getResources().getResourceName(resId));
                }
                // We have a cached drawable, return it!
                return dr;
            }

            if (tv.string != null && tv.string.toString().endsWith(".xml")) {
                // If the resource is an XML file, let's try and parse it
                try {
                    final XmlPullParser parser = res.getXml(resId);
                    final AttributeSet attrs = Xml.asAttributeSet(parser);
                    int type;
                    while ((type = parser.next()) != XmlPullParser.START_TAG &&
                            type != XmlPullParser.END_DOCUMENT) {
                        // Empty loop
                    }
                    if (type != XmlPullParser.START_TAG) {
                        throw new XmlPullParserException("No start tag found");
                    }

                    final String tagName = parser.getName();
                    // Add the tag name to the cache
                    mKnownDrawableIdTags.append(resId, tagName);

                    // Now try and find a delegate for the tag name and inflate if found
                    final InflateDelegate delegate = mDelegates.get(tagName);
                    if (delegate != null) {
                        dr = delegate.createFromXmlInner(context, parser, attrs,
                                context.getTheme());
                    }
                    if (dr != null) {
                        // Add it to the drawable cache
                        dr.setChangingConfigurations(tv.changingConfigurations);
                        if (addCachedDelegateDrawable(context, key, dr) && DEBUG) {
                            Log.i(TAG, "[loadDrawableFromDelegates] Saved drawable to cache: " +
                                    context.getResources().getResourceName(resId));
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Exception while inflating drawable", e);
                }
            }
            if (dr == null) {
                // If we reach here then the delegate inflation of the resource failed. Mark it as
                // bad so we skip the id next time
                mKnownDrawableIdTags.append(resId, SKIP_DRAWABLE_TAG);
            }
            return dr;
        }

        return null;
    }

    private Drawable getCachedDelegateDrawable(@NonNull final Context context, final long key) {
        synchronized (mDelegateDrawableCacheLock) {
            final LongSparseArray<WeakReference<ConstantState>> cache
                    = mDelegateDrawableCaches.get(context);
            if (cache == null) {
                return null;
            }

            final WeakReference<ConstantState> wr = cache.get(key);
            if (wr != null) {
                // We have the key, and the secret
                ConstantState entry = wr.get();
                if (entry != null) {
                    return entry.newDrawable(context.getResources());
                } else {
                    // Our entry has been purged
                    cache.delete(key);
                }
            }
        }
        return null;
    }

    private boolean addCachedDelegateDrawable(@NonNull final Context context, final long key,
            @NonNull final Drawable drawable) {
        final ConstantState cs = drawable.getConstantState();
        if (cs != null) {
            synchronized (mDelegateDrawableCacheLock) {
                LongSparseArray<WeakReference<ConstantState>> cache
                        = mDelegateDrawableCaches.get(context);
                if (cache == null) {
                    cache = new LongSparseArray<>();
                    mDelegateDrawableCaches.put(context, cache);
                }
                cache.put(key, new WeakReference<ConstantState>(cs));
            }
            return true;
        }
        return false;
    }

    public final Drawable onDrawableLoadedFromResources(@NonNull Context context,
            @NonNull TintResources resources, @DrawableRes final int resId) {
        Drawable drawable = loadDrawableFromDelegates(context, resId);
        if (drawable == null) {
            drawable = resources.superGetDrawable(resId);
        }
        if (drawable != null) {
            return tintDrawable(context, resId, false, drawable);
        }
        return null;
    }

    private static boolean tintDrawableUsingColorFilter(@NonNull Context context,
            @DrawableRes final int resId, @NonNull Drawable drawable) {
        PorterDuff.Mode tintMode = DEFAULT_MODE;
        boolean colorAttrSet = false;
        int colorAttr = 0;
        int alpha = -1;

        if (arrayContains(COLORFILTER_TINT_COLOR_CONTROL_NORMAL, resId)) {
            colorAttr = R.attr.colorControlNormal_ox;
            colorAttrSet = true;
        } else if (arrayContains(COLORFILTER_COLOR_CONTROL_ACTIVATED, resId)) {
            colorAttr = R.attr.colorControlActivated_ox;
            colorAttrSet = true;
        } else if (arrayContains(COLORFILTER_COLOR_BACKGROUND_MULTIPLY, resId)) {
            colorAttr = android.R.attr.colorBackground;
            colorAttrSet = true;
            tintMode = PorterDuff.Mode.MULTIPLY;
        } else   {
            colorAttr = android.R.attr.colorForeground;
            colorAttrSet = true;
            alpha = Math.round(0.16f * 255);
        }

        if (colorAttrSet) {
            if (DrawableUtils.canSafelyMutateDrawable(drawable)) {
                drawable = drawable.mutate();
            }

            final int color = ThemeUtils.getThemeAttrColor(context, colorAttr);
            drawable.setColorFilter(getPorterDuffColorFilter(color, tintMode));

            if (alpha != -1) {
                drawable.setAlpha(alpha);
            }

            if (DEBUG) {
                Log.d(TAG, "[tintDrawableUsingColorFilter] Tinted "
                        + context.getResources().getResourceName(resId) +
                        " with color: #" + Integer.toHexString(color));
            }
            return true;
        }
        return false;
    }

    private void addDelegate(@NonNull String tagName, @NonNull InflateDelegate delegate) {
        if (mDelegates == null) {
            mDelegates = new ArrayMap<>();
        }
        mDelegates.put(tagName, delegate);
    }

    private void removeDelegate(@NonNull String tagName, @NonNull InflateDelegate delegate) {
        if (mDelegates != null && mDelegates.get(tagName) == delegate) {
            mDelegates.remove(tagName);
        }
    }

    private static boolean arrayContains(int[] array, int value) {
        for (int id : array) {
            if (id == value) {
                return true;
            }
        }
        return false;
    }

    final PorterDuff.Mode getTintMode(final int resId) {
        PorterDuff.Mode mode = null;

        if (resId == R.drawable.abc_switch_thumb_material_ox) {
            mode = PorterDuff.Mode.MULTIPLY;
        }

        return mode;
    }

    public final ColorStateList getTintList(@NonNull Context context, @DrawableRes int resId) {
        // Try the cache first (if it exists)
        ColorStateList tint = getTintListFromCache(context, resId);

        if (tint == null) {
            // ...if the cache did not contain a color state list, try and create one
            if (resId == R.drawable.abc_edit_text_material_ox) {
                tint = createEditTextColorStateList(context);
            } else if (resId == R.drawable.abc_switch_track_mtrl_alpha_ox) {
                tint = createSwitchTrackColorStateList(context);
            } else if (resId == R.drawable.abc_switch_thumb_material_ox) {
                tint = createSwitchThumbColorStateList(context);
            } else if (resId == R.drawable.abc_btn_default_mtrl_shape_ox
                    || resId == R.drawable.abc_btn_borderless_material_ox) {
                tint = createDefaultButtonColorStateList(context);
            } else if (resId == R.drawable.abc_btn_colored_material_ox) {
                tint = createColoredButtonColorStateList(context);
            } else if (resId == R.drawable.abc_spinner_mtrl_am_alpha_ox
                    || resId == R.drawable.abc_spinner_textfield_background_material_ox) {
                tint = createSpinnerColorStateList(context);
            } else if (arrayContains(TINT_COLOR_CONTROL_NORMAL, resId)) {
                tint = ThemeUtils.getThemeAttrColorStateList(context, R.attr.colorControlNormal_ox);
            } else if (arrayContains(TINT_COLOR_CONTROL_STATE_LIST, resId)) {
                tint = createDefaultColorStateList(context);
            } else if (arrayContains(TINT_CHECKABLE_BUTTON_LIST, resId)) {
                tint = createCheckableButtonColorStateList(context);
            } else if (resId == R.drawable.abc_seekbar_thumb_material_ox) {
                tint = createSeekbarThumbColorStateList(context);
            }

            if (tint != null) {
                addTintListToCache(context, resId, tint);
            }
        }
        return tint;
    }

    private ColorStateList getTintListFromCache(@NonNull Context context, @DrawableRes int resId) {
        if (mTintLists != null) {
            final SparseArray<ColorStateList> tints = mTintLists.get(context);
            return tints != null ? tints.get(resId) : null;
        }
        return null;
    }

    private void addTintListToCache(@NonNull Context context, @DrawableRes int resId,
            @NonNull ColorStateList tintList) {
        if (mTintLists == null) {
            mTintLists = new WeakHashMap<>();
        }
        SparseArray<ColorStateList> themeTints = mTintLists.get(context);
        if (themeTints == null) {
            themeTints = new SparseArray<>();
            mTintLists.put(context, themeTints);
        }
        themeTints.append(resId, tintList);
    }

    private ColorStateList createDefaultColorStateList(Context context) {
        /**
         * Generate the default color state list which uses the colorControl attributes.
         * Order is important here. The default enabled state needs to go at the bottom.
         */

        final int colorControlNormal = ThemeUtils.getThemeAttrColor(context, R.attr.colorControlNormal_ox);
        final int colorControlActivated = ThemeUtils.getThemeAttrColor(context, R.attr.colorControlActivated_ox);

        final int[][] states = new int[7][];
        final int[] colors = new int[7];
        int i = 0;

        // Disabled state
        states[i] = ThemeUtils.DISABLED_STATE_SET;
        colors[i] = ThemeUtils.getDisabledThemeAttrColor(context, R.attr.colorControlNormal_ox);
        i++;

        states[i] = ThemeUtils.FOCUSED_STATE_SET;
        colors[i] = colorControlActivated;
        i++;

        states[i] = ThemeUtils.ACTIVATED_STATE_SET;
        colors[i] = colorControlActivated;
        i++;

        states[i] = ThemeUtils.PRESSED_STATE_SET;
        colors[i] = colorControlActivated;
        i++;

        states[i] = ThemeUtils.CHECKED_STATE_SET;
        colors[i] = colorControlActivated;
        i++;

        states[i] = ThemeUtils.SELECTED_STATE_SET;
        colors[i] = colorControlActivated;
        i++;

        // Default enabled state
        states[i] = ThemeUtils.EMPTY_STATE_SET;
        colors[i] = colorControlNormal;
        i++;

        return new ColorStateList(states, colors);
    }

    private ColorStateList createCheckableButtonColorStateList(Context context) {
        final int[][] states = new int[3][];
        final int[] colors = new int[3];
        int i = 0;

        // Disabled state
        states[i] = ThemeUtils.DISABLED_STATE_SET;
        colors[i] = ThemeUtils.getDisabledThemeAttrColor(context, R.attr.colorControlNormal_ox);
        i++;

        states[i] = ThemeUtils.CHECKED_STATE_SET;
        colors[i] = ThemeUtils.getThemeAttrColor(context, R.attr.colorControlActivated_ox);
        i++;

        // Default enabled state
        states[i] = ThemeUtils.EMPTY_STATE_SET;
        colors[i] = ThemeUtils.getThemeAttrColor(context, R.attr.colorControlNormal_ox);
        i++;

        return new ColorStateList(states, colors);
    }

    private ColorStateList createSwitchTrackColorStateList(Context context) {
        final int[][] states = new int[3][];
        final int[] colors = new int[3];
        int i = 0;

        // Disabled state
        states[i] = ThemeUtils.DISABLED_STATE_SET;
        colors[i] = ThemeUtils.getThemeAttrColor(context, android.R.attr.colorForeground, 0.1f);
        i++;

        states[i] = ThemeUtils.CHECKED_STATE_SET;
        colors[i] = ThemeUtils.getThemeAttrColor(context, R.attr.colorControlActivated_ox, 0.3f);
        i++;

        // Default enabled state
        states[i] = ThemeUtils.EMPTY_STATE_SET;
        colors[i] = ThemeUtils.getThemeAttrColor(context, android.R.attr.colorForeground, 0.3f);
        i++;

        return new ColorStateList(states, colors);
    }

    private ColorStateList createSwitchThumbColorStateList(Context context) {
        final int[][] states = new int[3][];
        final int[] colors = new int[3];
        int i = 0;

        final ColorStateList thumbColor = ThemeUtils.getThemeAttrColorStateList(context,
                R.attr.colorSwitchThumbNormal_ox);

        if (thumbColor != null && thumbColor.isStateful()) {
            // If colorSwitchThumbNormal is a valid ColorStateList, extract the default and
            // disabled colors from it

            // Disabled state
            states[i] = ThemeUtils.DISABLED_STATE_SET;
            colors[i] = thumbColor.getColorForState(states[i], 0);
            i++;

            states[i] = ThemeUtils.CHECKED_STATE_SET;
            colors[i] = ThemeUtils.getThemeAttrColor(context, R.attr.colorControlActivated_ox);
            i++;

            // Default enabled state
            states[i] = ThemeUtils.EMPTY_STATE_SET;
            colors[i] = thumbColor.getDefaultColor();
            i++;
        } else {
            // Else we'll use an approximation using the default disabled alpha

            // Disabled state
            states[i] = ThemeUtils.DISABLED_STATE_SET;
            colors[i] = ThemeUtils.getDisabledThemeAttrColor(context, R.attr.colorSwitchThumbNormal_ox);
            i++;

            states[i] = ThemeUtils.CHECKED_STATE_SET;
            colors[i] = ThemeUtils.getThemeAttrColor(context, R.attr.colorControlActivated_ox);
            i++;

            // Default enabled state
            states[i] = ThemeUtils.EMPTY_STATE_SET;
            colors[i] = ThemeUtils.getThemeAttrColor(context, R.attr.colorSwitchThumbNormal_ox);
            i++;
        }

        return new ColorStateList(states, colors);
    }

    private ColorStateList createEditTextColorStateList(Context context) {
        final int[][] states = new int[3][];
        final int[] colors = new int[3];
        int i = 0;

        // Disabled state
        states[i] = ThemeUtils.DISABLED_STATE_SET;
        colors[i] = ThemeUtils.getDisabledThemeAttrColor(context, R.attr.colorControlNormal_ox);
        i++;

        states[i] = ThemeUtils.NOT_PRESSED_OR_FOCUSED_STATE_SET;
        colors[i] = ThemeUtils.getThemeAttrColor(context, R.attr.colorControlNormal_ox);
        i++;

        // Default enabled state
        states[i] = ThemeUtils.EMPTY_STATE_SET;
        colors[i] = ThemeUtils.getThemeAttrColor(context, R.attr.colorControlActivated_ox);
        i++;

        return new ColorStateList(states, colors);
    }

    private ColorStateList createDefaultButtonColorStateList(Context context) {
        return createButtonColorStateList(context, R.attr.colorButtonNormal_ox);
    }

    private ColorStateList createColoredButtonColorStateList(Context context) {
        return createButtonColorStateList(context, R.attr.colorAccent_ox);
    }

    private ColorStateList createButtonColorStateList(Context context, int baseColorAttr) {
        final int[][] states = new int[4][];
        final int[] colors = new int[4];
        int i = 0;

        final int baseColor = ThemeUtils.getThemeAttrColor(context, baseColorAttr);
        final int colorControlHighlight = ThemeUtils.getThemeAttrColor(context, R.attr.colorControlHighlight_ox);

        // Disabled state
        states[i] = ThemeUtils.DISABLED_STATE_SET;
        colors[i] = ThemeUtils.getDisabledThemeAttrColor(context, R.attr.colorButtonNormal_ox);
        i++;

        states[i] = ThemeUtils.PRESSED_STATE_SET;
        colors[i] = ColorUtils.compositeColors(colorControlHighlight, baseColor);
        i++;

        states[i] = ThemeUtils.FOCUSED_STATE_SET;
        colors[i] = ColorUtils.compositeColors(colorControlHighlight, baseColor);
        i++;

        // Default enabled state
        states[i] = ThemeUtils.EMPTY_STATE_SET;
        colors[i] = baseColor;
        i++;

        return new ColorStateList(states, colors);
    }

    private ColorStateList createSpinnerColorStateList(Context context) {
        final int[][] states = new int[3][];
        final int[] colors = new int[3];
        int i = 0;

        // Disabled state
        states[i] = ThemeUtils.DISABLED_STATE_SET;
        colors[i] = ThemeUtils.getDisabledThemeAttrColor(context, R.attr.colorControlNormal_ox);
        i++;

        states[i] = ThemeUtils.NOT_PRESSED_OR_FOCUSED_STATE_SET;
        colors[i] = ThemeUtils.getThemeAttrColor(context, R.attr.colorControlNormal_ox);
        i++;

        states[i] = ThemeUtils.EMPTY_STATE_SET;
        colors[i] = ThemeUtils.getThemeAttrColor(context, R.attr.colorControlActivated_ox);
        i++;

        return new ColorStateList(states, colors);
    }

    private ColorStateList createSeekbarThumbColorStateList(Context context) {
        final int[][] states = new int[2][];
        final int[] colors = new int[2];
        int i = 0;

        // Disabled state
        states[i] = ThemeUtils.DISABLED_STATE_SET;
        colors[i] = ThemeUtils.getDisabledThemeAttrColor(context, R.attr.colorControlActivated_ox);
        i++;

        states[i] = ThemeUtils.EMPTY_STATE_SET;
        colors[i] = ThemeUtils.getThemeAttrColor(context, R.attr.colorControlActivated_ox);
        i++;

        return new ColorStateList(states, colors);
    }

    private static class ColorFilterLruCache extends LruCache<Integer, PorterDuffColorFilter> {

        public ColorFilterLruCache(int maxSize) {
            super(maxSize);
        }

        PorterDuffColorFilter get(int color, PorterDuff.Mode mode) {
            return get(generateCacheKey(color, mode));
        }

        PorterDuffColorFilter put(int color, PorterDuff.Mode mode, PorterDuffColorFilter filter) {
            return put(generateCacheKey(color, mode), filter);
        }

        private static int generateCacheKey(int color, PorterDuff.Mode mode) {
            int hashCode = 1;
            hashCode = 31 * hashCode + color;
            hashCode = 31 * hashCode + mode.hashCode();
            return hashCode;
        }
    }

    public static void tintDrawable(Drawable drawable, TintInfo tint, int[] state) {
        if (DrawableUtils.canSafelyMutateDrawable(drawable)
                && drawable.mutate() != drawable) {
            Log.d(TAG, "Mu d nstance as the input.");
            return;
        }

        if (tint.mHasTintList || tint.mHasTintMode) {
            drawable.setColorFilter(createTintFilter(
                    tint.mHasTintList ? tint.mTintList : null,
                    tint.mHasTintMode ? tint.mTintMode : DEFAULT_MODE,
                    state));
        } else {
            drawable.clearColorFilter();
        }

        if (Build.VERSION.SDK_INT <= 23) {
            // Pre-v23 there is no guarantee that a state change will invoke an invalidation,
            // so we force it ourselves
            drawable.invalidateSelf();
        }
    }

    private static PorterDuffColorFilter createTintFilter(ColorStateList tint,
            PorterDuff.Mode tintMode, final int[] state) {
        if (tint == null || tintMode == null) {
            return null;
        }
        final int color = tint.getColorForState(state, Color.TRANSPARENT);
        return getPorterDuffColorFilter(color, tintMode);
    }

    public static PorterDuffColorFilter getPorterDuffColorFilter(int color, PorterDuff.Mode mode) {
        // First, lets see if the cache already contains the color filter
        PorterDuffColorFilter filter = COLOR_FILTER_CACHE.get(color, mode);

        if (filter == null) {
            // Cache miss, so create a color filter and add it to the cache
            filter = new PorterDuffColorFilter(color, mode);
            COLOR_FILTER_CACHE.put(color, mode, filter);
        }

        return filter;
    }

    private static void setPorterDuffColorFilter(Drawable d, int color, PorterDuff.Mode mode) {
        if (DrawableUtils.canSafelyMutateDrawable(d)) {
            d = d.mutate();
        }
        d.setColorFilter(getPorterDuffColorFilter(color, mode == null ? DEFAULT_MODE : mode));
    }

    private static boolean isVectorDrawable(@NonNull Drawable d) {
        return true
                 ;
    }

    private static class VdcInflateDelegate implements InflateDelegate {
        @Override
        public Drawable createFromXmlInner(@NonNull Context context, @NonNull XmlPullParser parser,
                @NonNull AttributeSet attrs, @Nullable Resources.Theme theme) {
            try {
                return null;
            } catch (Exception e) {
                Log.e("VdcInflateDelegate", "Exception while inflating <vector>", e);
                return null;
            }
        }
    }

    private static class AvdcInflateDelegate implements InflateDelegate {
        @Override
        public Drawable createFromXmlInner(@NonNull Context context, @NonNull XmlPullParser parser,
                @NonNull AttributeSet attrs, @Nullable Resources.Theme theme) {
            try {
                return null;
            } catch (Exception e) {
                Log.e("AvdcInflateDelegate", "Exception while inflating <animated-vector>", e);
                return null;
            }
        }
    }
}
