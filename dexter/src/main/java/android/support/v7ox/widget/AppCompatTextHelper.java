/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Build;
//import android.support.v7ox.appcompat.R;
import com.karumi.dexterox.R;
import android.support.v7ox.text.AllCapsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.AttributeSet;
import android.widget.TextView;

class AppCompatTextHelper {

    static AppCompatTextHelper create(TextView textView) {
        if (Build.VERSION.SDK_INT >= 17) {
            return new AppCompatTextHelperV17(textView);
        }
        return new AppCompatTextHelper(textView);
    }

    private static final int[] VIEW_ATTRS = {android.R.attr.textAppearance,
            android.R.attr.drawableLeft, android.R.attr.drawableTop,
            android.R.attr.drawableRight, android.R.attr.drawableBottom };
    private static final int[] TEXT_APPEARANCE_ATTRS = {R.attr.textAllCaps_ox};

    final TextView mView;

    private TintInfo mDrawableLeftTint;
    private TintInfo mDrawableTopTint;
    private TintInfo mDrawableRightTint;
    private TintInfo mDrawableBottomTint;

    AppCompatTextHelper(TextView view) {
        mView = view;
    }

    void loadFromAttributes(AttributeSet attrs, int defStyleAttr) {
        final Context context = mView.getContext();
        final AppCompatDrawableManager drawableManager = AppCompatDrawableManager.get();

        // First read the TextAppearance style id
        TypedArray a = context.obtainStyledAttributes(attrs, VIEW_ATTRS, defStyleAttr, 0);
        final int ap = a.getResourceId(0, -1);

        // Now read the compound drawable and grab any tints
        if (a.hasValue(1)) {
            mDrawableLeftTint = createTintInfo(context, drawableManager, a.getResourceId(1, 0));
        }
        if (a.hasValue(2)) {
            mDrawableTopTint = createTintInfo(context, drawableManager, a.getResourceId(2, 0));
        }
        if (a.hasValue(3)) {
            mDrawableRightTint = createTintInfo(context, drawableManager, a.getResourceId(3, 0));
        }
        if (a.hasValue(4)) {
            mDrawableBottomTint = createTintInfo(context, drawableManager, a.getResourceId(4, 0));
        }
        a.recycle();

        if (!(mView.getTransformationMethod() instanceof PasswordTransformationMethod)) {
            // PasswordTransformationMethod wipes out all other TransformationMethod instances
            // in TextView's constructor, so we should only set a new transformation method
            // if we don't have a PasswordTransformationMethod currently...

            boolean allCaps = false;
            boolean allCapsSet = false;

            // First check TextAppearance's textAllCaps_ox value
            if (ap != -1) {
                TypedArray appearance = context
                        .obtainStyledAttributes(ap, R.styleable.TextAppearance);
                if (appearance.hasValue(R.styleable.TextAppearance_textAllCaps_ox) ){
                    allCapsSet = true;
                    allCaps = appearance.getBoolean(R.styleable.TextAppearance_textAllCaps_ox, false);
                }
                appearance.recycle();
            }

            // Now read the style's value
            a = context.obtainStyledAttributes(attrs, TEXT_APPEARANCE_ATTRS, defStyleAttr, 0);
            if (a.hasValue(0)) {
                allCapsSet = true;
                allCaps = a.getBoolean(0, false);
            }
            a.recycle();

            if (allCapsSet) {
                setAllCaps(allCaps);
            }
        }
    }

    void onSetTextAppearance(Context context, int resId) {
        TypedArray appearance = context.obtainStyledAttributes(resId, TEXT_APPEARANCE_ATTRS);
        if (appearance.getBoolean(0, false)) {
            // This follows the logic in TextView.setTextAppearance that serves as an "overlay"
            // on the current state of the TextView. Here we only allow turning all-caps on when
            // the passed style has textAllCaps_ox attribute set to true.
            setAllCaps(true);
        }
        appearance.recycle();
    }

    void setAllCaps(boolean allCaps) {
        mView.setTransformationMethod(allCaps
                ? new AllCapsTransformationMethod(mView.getContext())
                : null);
    }

    void applyCompoundDrawablesTints() {
        if (mDrawableLeftTint != null || mDrawableTopTint != null ||
                mDrawableRightTint != null || mDrawableBottomTint != null) {
            final Drawable[] compoundDrawables = mView.getCompoundDrawables();
            applyCompoundDrawableTint(compoundDrawables[0], mDrawableLeftTint);
            applyCompoundDrawableTint(compoundDrawables[1], mDrawableTopTint);
            applyCompoundDrawableTint(compoundDrawables[2], mDrawableRightTint);
            applyCompoundDrawableTint(compoundDrawables[3], mDrawableBottomTint);
        }
    }

    final void applyCompoundDrawableTint(Drawable drawable, TintInfo info) {
        if (drawable != null && info != null) {
            AppCompatDrawableManager.tintDrawable(drawable, info, mView.getDrawableState());
        }
    }

    protected static TintInfo createTintInfo(Context context,
            AppCompatDrawableManager drawableManager, int drawableId) {
        final ColorStateList tintList = drawableManager.getTintList(context, drawableId);
        if (tintList != null) {
            final TintInfo tintInfo = new TintInfo();
            tintInfo.mHasTintList = true;
            tintInfo.mTintList = tintList;
            return tintInfo;
        }
        return null;
    }
}
