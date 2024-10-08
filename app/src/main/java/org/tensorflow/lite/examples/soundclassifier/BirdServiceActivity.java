/**
    Copyright (C) 2024 Forrest Guice
    This file is part of whoBIRD.

    whoBIRD is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    whoBIRD is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with whoBIRD.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.tensorflow.lite.examples.soundclassifier;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebSettings;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.tensorflow.lite.examples.soundclassifier.databinding.ActivityMainBinding;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

/**
 * This activity binds to the BirdService when started.
 */
public class BirdServiceActivity extends AppCompatActivity
{
    protected BirdService birdService;
    protected boolean boundToService = false;
    protected ActivityMainBinding binding;
    protected boolean startAutomatically = false;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        initViews();
        requestPermissions();
    }

    protected final ServiceConnection serviceConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service)
        {
            BirdService.BirdServiceBinder binder = (BirdService.BirdServiceBinder) service;
            birdService = binder.getService();
            boundToService = true;
            birdService.addServiceListener(serviceListener);
            updateViews();

            if (!birdService.isRecording() && startAutomatically) {
                birdService.startRecording();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            boundToService = false;
        }
    };

    private final BirdService.BirdServiceListener serviceListener = new BirdService.BirdServiceListener()
    {
        @Override
        public SoundClassifier.SoundClassifierUI getUI() {
            return activityUI;
        }

        @Override
        public void onStateChanged(boolean isActive) {
            updateViews();
        }
    };

    @Override
    protected void onStart()
    {
        super.onStart();
        startForegroundService(new Intent(this, BirdService.class));
        bindService( new Intent(this, BirdService.class),
                serviceConnection, Context.BIND_AUTO_CREATE );
    }

    @Override
    protected void onStop()
    {
        birdService.removeServiceListener(serviceListener);
        unbindService(serviceConnection);
        boundToService = false;
        super.onStop();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (!checkMicrophonePermission(this)) {
            Toast.makeText(BirdServiceActivity.this, getString(R.string.error_audio_permission), Toast.LENGTH_SHORT).show();
        }
        updateViews();
    }

    protected void initViews()
    {
        BottomNavigationView navigationView = findViewById(R.id.bottomNavigationView);
        if (navigationView != null) {
            navigationView.setOnItemSelectedListener(Navigation.getOnItemSelectedListener(this));
        }
        binding.fab.setOnClickListener(onFabClicked);
        binding.checkShowImages.setOnCheckedChangeListener(onCheckShowImages);
        binding.checkIgnoreMeta.setOnCheckedChangeListener(onCheckIgnoreMeta);

        binding.text1.setText("");
        binding.text2.setText("");
        binding.gps.setText(getString(R.string.latitude)+": --.-- / " + getString(R.string.longitude) + ": --.--" );   // TODO: i18n

        // TODO: set aspect ratio for webview and icon
        binding.webview.setWebViewClient(new MlWebViewClient(this));
        binding.webview.getSettings().setDomStorageEnabled(true);
        binding.webview.getSettings().setJavaScriptEnabled(true);

        if (GithubStar.shouldShowStarDialog(this)) {
            GithubStar.starDialog(this, "https://github.com/woheller69/whoBIRD");
        }
    }

    protected void updateViews()
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean showImages = prefs.getBoolean("main_show_images", false);
        binding.checkShowImages.setChecked(showImages);

        boolean ignoreMeta = prefs.getBoolean("main_ignore_meta", false);
        binding.checkIgnoreMeta.setChecked(ignoreMeta);

        if (boundToService)
        {
            boolean isRecording = birdService.isRecording();
            binding.progressHorizontal.setIndeterminate(isRecording);
            binding.fab.setImageDrawable(ContextCompat.getDrawable(this, isRecording ? R.drawable.ic_pause_24dp
                                                                                             : R.drawable.ic_play_24dp));
        } else {
            binding.progressHorizontal.setIndeterminate(false);
            binding.text1.setText("");
            binding.text2.setText("");
        }
    }

    private final SoundClassifier.SoundClassifierUI activityUI = new SoundClassifier.SoundClassifierUI()
    {
        @Override
        public boolean ignoreMeta() {
            return binding.checkIgnoreMeta.isChecked();
        }

        @Override
        public boolean isShowingProgress() {
            return binding.progressHorizontal.isIndeterminate();
        }

        @Override
        public void setLocationText(float lat, float lon) {
            binding.gps.setText(getString(R.string.latitude) + ": " + (Math.round(lat *100.0) /100.0) + " / " + getString(R.string.longitude) + ": " + (Math.round(lon *100.0) /100));
        }

        @Override
        public void setPrimaryText(@NonNull String text) {
            binding.text1.setText(text);
            binding.text1.setBackgroundResource(0);
        }

        @Override
        public void setPrimaryText(float value, @NonNull String label) {
            updateTextView(value, label, binding.text1);
        }

        @Override
        public void setSecondaryText(@NonNull String text) {
            binding.text2.setText(text);
            binding.text2.setBackgroundResource(0);
        }

        @Override
        public void setSecondaryText(float value, @NonNull String label) {
            updateTextView(value, label, binding.text1);
        }

        @Override
        public boolean showImages() {
            return binding.checkShowImages.isChecked();
        }

        @Nullable
        @Override
        public String getImageURL() {
            return binding.webview.getUrl();
        }

        private void updateTextView(float value, String label, TextView view)
        {
            view.setText(label + "  " + Math.round(value * 100.0) + "%");   // TODO: i18n
            view.setBackgroundResource(getBackgroundResource(value));
        }

        protected int getBackgroundResource(float value) {
            if (value < 0.3) return R.drawable.oval_holo_red_dark_dotted;
            else if (value < 0.5) return R.drawable.oval_holo_red_dark;
            else if (value < 0.65) return R.drawable.oval_holo_orange_dark;
            else if (value < 0.8) return R.drawable.oval_holo_orange_light;
            else return R.drawable.oval_holo_green_light;
        }

        @Override
        public void showImage(@NonNull String label, @Nullable String url)
        {
            if (url == null || url == "about:blank") {
                binding.webview.setVisibility(View.GONE);
                binding.icon.setVisibility(View.VISIBLE);
                binding.webviewUrl.setText("");
                binding.webviewUrl.setVisibility(View.GONE);
                binding.webviewName.setText("");
                binding.webviewName.setVisibility(View.GONE);
                binding.webviewLatinname.setText("");
                binding.webviewLatinname.setVisibility(View.GONE);
                binding.webviewReload.setVisibility(View.GONE);
            } else {
                if (binding.webview.getUrl() != url) {
                    binding.webview.setVisibility(View.INVISIBLE);
                    binding.webview.getSettings().setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
                    binding.webview.loadUrl("javascript:document.open();document.close();");  //clear view
                    binding.webview.loadUrl(url);
                    binding.webviewUrl.setText(url);
                    binding.webviewUrl.setVisibility(View.VISIBLE);
                    binding.webviewName.setText(label);
                    binding.webviewLatinname.setText(label);
                    binding.webviewLatinname.setVisibility(View.VISIBLE);
                    binding.webviewName.setVisibility(View.VISIBLE);
                    binding.webviewReload.setVisibility(View.VISIBLE);
                    binding.icon.setVisibility(View.GONE);
                }
            }
        }

        @Override
        public void hideImage() {
            binding.webview.setVisibility(View.GONE);
            binding.icon.setVisibility(View.VISIBLE);
            binding.webview.loadUrl("about:blank");
            binding.webviewUrl.setText("");
            binding.webviewUrl.setVisibility(View.GONE);
            binding.webviewName.setText("");
            binding.webviewName.setVisibility(View.GONE);
            binding.webviewLatinname.setText("");
            binding.webviewLatinname.setVisibility(View.GONE);
            binding.webviewReload.setVisibility(View.GONE);
        }
    };

    private final View.OnClickListener onFabClicked = new View.OnClickListener()
    {
        @Override
        public void onClick(View view)
        {
            if (boundToService) {
                if (birdService.isRecording()) {
                    birdService.stopRecording();
                } else {
                    birdService.startRecording();
                }
            }
        }
    };

    private final CompoundButton.OnCheckedChangeListener onCheckShowImages = new CompoundButton.OnCheckedChangeListener()
    {
        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean b)
        {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(BirdServiceActivity.this);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("main_show_images", compoundButton.isChecked());
            editor.apply();
        }
    };

    private final CompoundButton.OnCheckedChangeListener onCheckIgnoreMeta = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean b)
        {
            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(BirdServiceActivity.this).edit();
            editor.putBoolean("main_ignore_meta", compoundButton.isChecked());
            editor.apply();
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (item.getItemId() == R.id.action_share_app)
        {
            Navigation.shareApp(BirdServiceActivity.this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Request Permissions; RECORD_AUDIO, ACCESS_LOCATION
     */
    public static final int REQUEST_PERMISSIONS = 1337;
    protected void requestPermissions()
    {
        Log.d("DEBUG", "requestPermissions");
        List<String> values = new ArrayList<>();
        if (!checkMicrophonePermission(this)) {
            values.add(Manifest.permission.RECORD_AUDIO);
        }
        if (!checkLocationPermission(this)) {
            values.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            values.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkNotificationPermission(this)) {
                values.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
        if (!values.isEmpty()) {
            requestPermissions(values.toArray(new String[0]), REQUEST_PERMISSIONS);
        }
    }

    @TargetApi(33)
    protected static boolean checkNotificationPermission(Context context) {
        return (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED);
    }
    protected static boolean checkMicrophonePermission(Context context) {
        return (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);
    }
    protected static boolean checkLocationPermission(Context context) {
        return (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED);
    }
}
