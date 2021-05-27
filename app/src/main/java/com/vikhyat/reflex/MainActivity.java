package com.vikhyat.reflex;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.widget.RelativeLayout;

import view.ReflexView;

public class MainActivity extends AppCompatActivity {
    private ReflexView gameView;
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        RelativeLayout layout =findViewById(R.id.relativeLayout);
        gameView = new ReflexView(this,getPreferences(Context.MODE_PRIVATE),layout);

        layout.addView(gameView,0);
    }
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onPause(){
        super.onPause();
        gameView.pause();
    }
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onResume(){
        super.onResume();
        gameView.resume(this);
    }
}
