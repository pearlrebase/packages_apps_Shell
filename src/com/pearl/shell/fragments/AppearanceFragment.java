/*
 * Copyright (C) 2018 The Potato Open Sauce Project
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

package com.pearl.shell.fragments;

import android.app.Fragment;
import android.os.Bundle;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.graphics.Color;
import android.os.SystemProperties;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.provider.SearchIndexableResource;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceScreen;
import android.support.v14.preference.PreferenceFragment;
import android.util.Log;

import com.android.settings.R;
import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.wrapper.OverlayManagerWrapper;
import com.android.settings.wrapper.OverlayManagerWrapper.OverlayInfo;

import java.util.ArrayList;
import java.util.List;

public class AppearanceFragment extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener, Indexable {

    private static final String KEY_BASE_THEME = "base_theme";
    private static final String KEY_SYSUI_THEME = "systemui_theme";
    private static final String BASE_THEME_CATEGORY = "android.base_theme";
    private static final String ACCENT_COLOR = "accent_color";
    private static final String ACCENT_COLOR_PROP = "persist.sys.theme.accentcolor";

    private ColorPickerPreference mThemeColor;
    private ListPreference mSystemThemeBase;
    private Fragment mCurrentFragment = this;
    private OverlayManagerWrapper mOverlayService;
    private PackageManager mPackageManager;
    private CustomSeekBarPreference mCornerRadius;
    private CustomSeekBarPreference mContentPadding;
    private ListPreference mSystemUiThemePref;

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mSystemThemeBase) {
            String current = getTheme(BASE_THEME_CATEGORY);
            if (((String) newValue).equals(current))
                return true;
            mOverlayService.setEnabledExclusiveInCategory((String) newValue, UserHandle.myUserId());
            mSystemThemeBase.setSummary(getCurrentTheme(BASE_THEME_CATEGORY));
        } else if (preference == mSystemUiThemePref) {
            int value = Integer.parseInt((String) newValue);
            Settings.Secure.putInt(getContext().getContentResolver(), Settings.Secure.THEME_MODE, value);
            mSystemUiThemePref.setSummary(mSystemUiThemePref.getEntries()[value]);
        }
        } else if (preference == mThemeColor) {
            int color = (Integer) newValue;
            String hexColor = String.format("%08X", (0xFFFFFFFF & color));
            SystemProperties.set(ACCENT_COLOR_PROP, hexColor);
            mOverlayService.reloadAndroidAssets(UserHandle.USER_CURRENT);
            mOverlayService.reloadAssets("com.android.settings", UserHandle.USER_CURRENT);
            mOverlayService.reloadAssets("com.android.systemui", UserHandle.USER_CURRENT);
        return true;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.theme);
        // OMS and PMS setup
        mOverlayService = ServiceManager.getService(Context.OVERLAY_SERVICE) != null ? new OverlayManagerWrapper()
                : null;
        mPackageManager = getActivity().getPackageManager();
        setupBasePref();
        setupStylePref();
        setupAccentPref();
    }

    private void setupBasePref() {
        mSystemThemeBase = (ListPreference) findPreference(KEY_BASE_THEME);
        mSystemThemeBase.setSummary(getCurrentTheme(BASE_THEME_CATEGORY));

        String[] pkgs = getAvailableThemes(BASE_THEME_CATEGORY);
        CharSequence[] labels = new CharSequence[pkgs.length];
        for (int i = 0; i < pkgs.length; i++) {
            try {
                labels[i] = mPackageManager.getApplicationInfo(pkgs[i], 0).loadLabel(mPackageManager);
            } catch (PackageManager.NameNotFoundException e) {
                labels[i] = pkgs[i];
            }
        }

        mSystemThemeBase.setEntries(labels);
        mSystemThemeBase.setEntryValues(pkgs);
        mSystemThemeBase.setValue(getTheme(BASE_THEME_CATEGORY));
        mSystemThemeBase.setOnPreferenceChangeListener(this);
    }

    private void setupStylePref() {
        mSystemUiThemePref = (ListPreference) findPreference(KEY_SYSUI_THEME);
        int value = Settings.Secure.getInt(getContext().getContentResolver(), Settings.Secure.THEME_MODE, 0);
        int index = mSystemUiThemePref.findIndexOfValue(Integer.toString(value));
        mSystemUiThemePref.setValue(Integer.toString(value));
        mSystemUiThemePref.setSummary(mSystemUiThemePref.getEntries()[index]);
        mSystemUiThemePref.setOnPreferenceChangeListener(this);
    }

    private void setupAccentPref() {
        mThemeColor = (ColorPickerPreference) findPreference(ACCENT_COLOR);
        String colorVal = SystemProperties.get(ACCENT_COLOR_PROP, "-1");
        int color = "-1".equals(colorVal)
                ? Color.WHITE
                : Color.parseColor("#" + colorVal);
        mThemeColor.setNewPreviewColor(color);
        mThemeColor.setOnPreferenceChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER = new BaseSearchIndexProvider() {
        @Override
        public List<SearchIndexableResource> getXmlResourcesToIndex(Context context, boolean enabled) {
            List<SearchIndexableResource> indexables = new ArrayList<>();
            SearchIndexableResource indexable = new SearchIndexableResource(context);
            indexable.xmlResId = R.xml.theme;
            indexables.add(indexable);
            return indexables;
        }

        @Override
        public List<String> getNonIndexableKeys(Context context) {
            List<String> keys = super.getNonIndexableKeys(context);
            return keys;
        }
    };

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.SHELL;
    }

    // Theme/OMS handling methods
    private CharSequence getCurrentTheme(String category) {
        String currentPkg = getTheme(category);
        CharSequence label = null;
        try {
            label = mPackageManager.getApplicationInfo(currentPkg, 0).loadLabel(mPackageManager);
        } catch (PackageManager.NameNotFoundException e) {
            label = currentPkg;
        }
        return label;
    }

    private String[] getAvailableThemes(String category) {
        List<OverlayInfo> infos = mOverlayService.getOverlayInfosForTarget("android", UserHandle.myUserId());
        List<String> pkgs = new ArrayList<>(infos.size());
        for (int i = 0, size = infos.size(); i < size; i++) {
            if (isTheme(infos.get(i), category)) {
                pkgs.add(infos.get(i).packageName);
            }
        }
        return pkgs.toArray(new String[pkgs.size()]);
    }

    private String getTheme(String category) {
        List<OverlayInfo> infos = mOverlayService.getOverlayInfosForTarget("android", UserHandle.myUserId());
        for (int i = 0, size = infos.size(); i < size; i++) {
            if (infos.get(i).isEnabled() && isTheme(infos.get(i), category)) {
                return infos.get(i).packageName;
            }
        }
        return null;
    }

    private boolean isTheme(OverlayInfo oi, String category) {
        if (!category.equals(oi.category)) {
            return false;
        }
        try {
            PackageInfo pi = mPackageManager.getPackageInfo(oi.packageName, 0);
            return pi != null && !pi.isStaticOverlayPackage();
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
