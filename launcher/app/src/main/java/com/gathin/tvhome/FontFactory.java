package com.gathin.tvhome;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies the chosen UI font to inflated text views. Layouts set android:fontFamily="@string/font_*", which
 * resolves to an Outfit font resource; this factory maps that resource to a role and gives the view the same
 * role in the chosen family. Only TextView-family tags are intercepted; everything else returns null so the
 * inflater carries on as before.
 */
final class FontFactory implements LayoutInflater.Factory2 {
    private static final String WIDGET_PREFIX = "android.widget.";
    private static final int[] FONT_FAMILY_ATTR = {android.R.attr.fontFamily};

    /** Class name to "is a TextView"; failed lookups are stored too so each name is resolved once. */
    private static final ConcurrentHashMap<String, Boolean> TEXT_VIEW_CLASSES = new ConcurrentHashMap<>();

    private final String choice;

    private FontFactory(String choice) {
        this.choice = choice;
    }

    /**
     * Call from MainActivity.onCreate before setContentView: FontFactory.install(this). No-op for Outfit (the
     * default) or when the activity's inflater already has a factory, so the default path is untouched.
     */
    static void install(Activity activity) {
        String choice = FontChoice.current(activity);
        if (FontChoice.isOutfit(choice)) return;
        LayoutInflater inflater = activity.getLayoutInflater();
        if (inflater.getFactory2() != null || inflater.getFactory() != null) return;
        inflater.setFactory2(new FontFactory(choice));
    }

    /** The dot-less tags LayoutInflater resolves in android.widget that are TextViews. */
    static boolean isPlainTextViewTag(String name) {
        return "TextView".equals(name) || "Button".equals(name) || "EditText".equals(name);
    }

    @Override
    public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
        boolean plain = isPlainTextViewTag(name);
        if (!plain && (name.indexOf('.') < 0 || !isTextViewClass(name))) return null;
        View view;
        try {
            view = create(context, name, plain ? WIDGET_PREFIX : null, attrs);
        } catch (ClassNotFoundException e) {
            return null;
        }
        if (view instanceof TextView) apply((TextView) view, context, attrs);
        return view;
    }

    @Override
    public View onCreateView(String name, Context context, AttributeSet attrs) {
        return onCreateView(null, name, context, attrs);
    }

    private static View create(Context context, String name, String prefix, AttributeSet attrs)
            throws ClassNotFoundException {
        LayoutInflater inflater = LayoutInflater.from(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return inflater.createView(context, name, prefix, attrs);
        return inflater.createView(name, prefix, attrs);
    }

    private static boolean isTextViewClass(String name) {
        Boolean known = TEXT_VIEW_CLASSES.get(name);
        if (known != null) return known;
        boolean is;
        try {
            is = TextView.class.isAssignableFrom(Class.forName(name, false, FontFactory.class.getClassLoader()));
        } catch (ClassNotFoundException | LinkageError e) {
            is = false;
        }
        TEXT_VIEW_CLASSES.put(name, is);
        return is;
    }

    /** android:fontFamily as set on the tag or through its style="" (not the theme's default style). */
    @SuppressLint("ResourceType") // framework attr id, not an app styleable; obtainStyledAttributes accepts it
    private void apply(TextView view, Context context, AttributeSet attrs) {
        int resourceId;
        TypedArray array = context.obtainStyledAttributes(attrs, FONT_FAMILY_ATTR);
        try {
            TypedValue value = array.peekValue(0);
            resourceId = value == null ? 0 : value.resourceId;
        } finally {
            array.recycle();
        }
        FontChoice.Role role = FontChoice.roleForOutfitResource(resourceId);
        if (role == null) return;
        android.graphics.Typeface face = FontChoice.typeface(context, choice, role);
        if (face != null) view.setTypeface(face);
    }
}
