package br.com.livroandroid.carros.activity;

import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import br.com.livroandroid.carros.R;

/**
 * Created by Roberto on 19/11/2016.
 */

public class BaseActivity extends livroandroid.lib.activity.BaseActivity{
    // Configura a Toolbar
    protected void setUpToolbar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }
    }
}
