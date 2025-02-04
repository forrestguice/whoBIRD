/*
 * Copyright 2020 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// Modifications by woheller69

package org.tensorflow.lite.examples.soundclassifier

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebSettings
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import org.tensorflow.lite.examples.soundclassifier.databinding.ActivityMainBinding
import kotlin.math.round

class MainActivity : AppCompatActivity() {

  private lateinit var soundClassifier: SoundClassifier
  private lateinit var binding: ActivityMainBinding

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    //Set aspect ratio for webview and icon
    val width = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val windowMetrics = windowManager.currentWindowMetrics
      windowMetrics.bounds.width()
    } else {
      val displayMetrics = DisplayMetrics()
      windowManager.defaultDisplay.getMetrics(displayMetrics)
      displayMetrics.widthPixels
    }
    val paramsWebview: ViewGroup.LayoutParams = binding.webview.getLayoutParams() as ViewGroup.LayoutParams
    paramsWebview.height = (width / 1.8f).toInt()
    val paramsIcon: ViewGroup.LayoutParams = binding.icon.getLayoutParams() as ViewGroup.LayoutParams
    paramsIcon.height = (width / 1.8f).toInt()

    soundClassifier = SoundClassifier(this, MainActivityUI(), SoundClassifier.Options())
    binding.gps.setText(getString(R.string.latitude)+": --.-- / " + getString(R.string.longitude) + ": --.--" )
    binding.webview.setWebViewClient(object : MlWebViewClient(this) {})
    binding.webview.settings.setDomStorageEnabled(true)
    binding.webview.settings.setJavaScriptEnabled(true)

    binding.fab.setOnClickListener {
      if (binding.progressHorizontal.isIndeterminate) {
        binding.progressHorizontal.setIndeterminate(false)
        binding.fab.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_play_24dp))
      }
      else {
        binding.progressHorizontal.setIndeterminate(true)
        binding.fab.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_pause_24dp))
      }
    }
    binding.bottomNavigationView.setOnItemSelectedListener(Navigation.getOnItemSelectedListener(this))

    val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
    val isShowImagesActive = sharedPref.getBoolean("main_show_images", false)
    binding.checkShowImages.isChecked = isShowImagesActive
    binding.checkShowImages.setOnClickListener { view ->
      val editor=sharedPref.edit()
      if ((view as CompoundButton).isChecked) {
        editor.putBoolean("main_show_images", true)
        editor.apply()
      } else {
        editor.putBoolean("main_show_images", false)
        editor.apply()
      }
    }

    val isIgnoreLocationDateActive = sharedPref.getBoolean("main_ignore_meta", false)
    binding.checkIgnoreMeta.isChecked = isIgnoreLocationDateActive
    binding.checkIgnoreMeta.setOnClickListener { view ->
      val editor=sharedPref.edit()
      if ((view as CompoundButton).isChecked) {
        editor.putBoolean("main_ignore_meta", true)
        editor.apply()
      } else {
        editor.putBoolean("main_ignore_meta", false)
        editor.apply()
      }
    }

    if (GithubStar.shouldShowStarDialog(this)) GithubStar.starDialog(this, "https://github.com/woheller69/whoBIRD")

    requestPermissions()

  }

  override fun onResume() {
    super.onResume()
    LocationHelper.requestLocation(this, soundClassifier)
    if (!checkLocationPermission()){
      Toast.makeText(this, this.resources.getString(R.string.error_location_permission), Toast.LENGTH_SHORT).show()
    }
    if (checkMicrophonePermission()){
      soundClassifier.start()
    } else {
      Toast.makeText(this, this.resources.getString(R.string.error_audio_permission), Toast.LENGTH_SHORT).show()
    }
    keepScreenOn(true)
  }

  override fun onPause() {
    super.onPause()
    LocationHelper.stopLocation(this)
    if (soundClassifier.isRecording) soundClassifier.stop()
  }

  private fun checkMicrophonePermission(): Boolean {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO ) == PackageManager.PERMISSION_GRANTED) {
      return true
    } else {
      return false
    }
  }

  private fun checkLocationPermission(): Boolean {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
      return true
    } else {
      return false
    }
  }

  private fun requestPermissions() {
    val perms = mutableListOf<String>()
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
      perms.add(Manifest.permission.RECORD_AUDIO)
    }
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
      perms.add(Manifest.permission.ACCESS_COARSE_LOCATION)
      perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    if (!perms.isEmpty()) requestPermissions(perms.toTypedArray(), REQUEST_PERMISSIONS)
  }

  private fun keepScreenOn(enable: Boolean) =
    if (enable) {
      window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } else {
      window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

  companion object {
    const val REQUEST_PERMISSIONS = 1337
  }

  fun reload(view: View) {
    binding.webview.settings.setCacheMode(WebSettings.LOAD_DEFAULT)
    binding.webview.loadUrl(binding.webviewUrl.text.toString())
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    val inflater = menuInflater
    inflater.inflate(R.menu.main, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      R.id.action_share_app -> {
        val intent = Intent(Intent.ACTION_SEND)
        val shareBody = "https://f-droid.org/packages/org.woheller69.whobird/"
        intent.setType("text/plain")
        intent.putExtra(Intent.EXTRA_TEXT, shareBody)
        startActivity(Intent.createChooser(intent, ""))
        return true
      }
      else -> return super.onOptionsItemSelected(item)
    }
  }

  inner class MainActivityUI : SoundClassifier.SoundClassifierUI
  {
    override fun setLocationText(lat: Float, lon: Float) {
      val text : String = getString(R.string.latitude)+": " + (round(SoundClassifier.lat *100.0) /100.0).toString() + " / " + getString(R.string.longitude) + ": " + (round(
        SoundClassifier.lon *100.0) /100).toString()
      binding.gps.setText(text)
    }

    override fun setPrimaryText(text: String) {
      binding.text1.text = text;
      binding.text1.setBackgroundResource(0)
    }
    override fun setPrimaryText(value: Float, label: String) {
      updateTextView(value, label, binding.text1);
    }

    override fun setSecondaryText(text: String) {
      binding.text2.text = text;
      binding.text2.setBackgroundResource(0)
    }
    override fun setSecondaryText(value: Float, label: String) {
      updateTextView(value, label, binding.text2);
    }

    override fun getImageURL(): String? {
      return binding.webview.url
    }

    override fun ignoreMeta(): Boolean {
      return binding.checkIgnoreMeta.isChecked
    }

    override fun showImages(): Boolean {
      return binding.checkShowImages.isChecked
    }

    override fun isShowingProgress(): Boolean {
      return binding.progressHorizontal.isIndeterminate;
    }

    private fun updateTextView(value: Float, label: String, tv: TextView)
    {
      tv.text = label + "  " + Math.round(value * 100.0) + "%"
      if (value < 0.3) tv.setBackgroundResource(R.drawable.oval_holo_red_dark_dotted)
      else if (value < 0.5) tv.setBackgroundResource(R.drawable.oval_holo_red_dark)
      else if (value < 0.65) tv.setBackgroundResource(R.drawable.oval_holo_orange_dark)
      else if (value < 0.8) tv.setBackgroundResource(R.drawable.oval_holo_orange_light)
      else tv.setBackgroundResource(R.drawable.oval_holo_green_light)
    }

    override fun showImage(label: String, url: String?) {
      if (url == null || url == "about:blank") {
        binding.webview.setVisibility(View.GONE)
        binding.icon.setVisibility(View.VISIBLE)
        binding.webviewUrl.setText("")
        binding.webviewUrl.setVisibility(View.GONE)
        binding.webviewName.setText("")
        binding.webviewName.setVisibility(View.GONE)
        binding.webviewLatinname.setText("")
        binding.webviewLatinname.setVisibility(View.GONE)
        binding.webviewReload.setVisibility(View.GONE)
      } else {
        if (binding.webview.url != url) {
          binding.webview.setVisibility(View.INVISIBLE)
          binding.webview.settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK)
          binding.webview.loadUrl("javascript:document.open();document.close();")  //clear view
          binding.webview.loadUrl(url)
          binding.webviewUrl.setText(url)
          binding.webviewUrl.setVisibility(View.VISIBLE)
          binding.webviewName.setText(label)
          binding.webviewLatinname.setText(label)
          binding.webviewLatinname.setVisibility(View.VISIBLE)
          binding.webviewName.setVisibility(View.VISIBLE)
          binding.webviewReload.setVisibility(View.VISIBLE)
          binding.icon.setVisibility(View.GONE)
        }
      }
    }

    override fun hideImage()
    {
      binding.webview.setVisibility(View.GONE)
      binding.icon.setVisibility(View.VISIBLE)
      binding.webview.loadUrl("about:blank")
      binding.webviewUrl.setText("")
      binding.webviewUrl.setVisibility(View.GONE)
      binding.webviewName.setText("")
      binding.webviewName.setVisibility(View.GONE)
      binding.webviewLatinname.setText("")
      binding.webviewLatinname.setVisibility(View.GONE)
      binding.webviewReload.setVisibility(View.GONE)
    }
  }

}
