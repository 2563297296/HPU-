package cn.edu.hpu.powerwidget;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

/** First-launch guide shown once before room selection. */
public final class OnboardingActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);
        Button start = findViewById(R.id.button_start);
        start.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                setResult(RESULT_OK);
                finish();
            }
        });
    }
}
