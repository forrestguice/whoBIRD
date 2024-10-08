package org.tensorflow.lite.examples.soundclassifier;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.MenuItem;

import com.google.android.material.navigation.NavigationBarView;

import androidx.annotation.NonNull;

public class Navigation
{
    public static NavigationBarView.OnItemSelectedListener getOnItemSelectedListener(final Activity activity)
    {
        return new NavigationBarView.OnItemSelectedListener()
        {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                if (item.getItemId() == R.id.action_about) {
                    activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/woheller69/whobird")));

                } else if (item.getItemId() == R.id.action_mic) {
                    Intent intent = new Intent(activity, getMainActivity());
                    activity.startActivity(intent);

                } else if (item.getItemId() == R.id.action_view) {
                    Intent intent = new Intent(activity, ViewActivity.class);
                    activity.startActivity(intent);

                } else if (item.getItemId() == R.id.action_settings) {
                    Intent intent = new Intent(activity, SettingsActivity.class);
                    activity.startActivity(intent);
                }
                return true;
            }
        };
    }

    public static Class getMainActivity() {
        return MainActivity.class;
    }

    public static void shareApp(Activity activity)
    {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, "https://f-droid.org/packages/org.woheller69.whobird/");
        activity.startActivity(Intent.createChooser(intent, ""));
    }
}
